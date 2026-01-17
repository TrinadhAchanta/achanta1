import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DnsParser {
    private static final Map<Integer, String> TYPE_MAP = Map.of(
            1, "A",
            5, "CNAME",
            28, "AAAA"
    );
    private static final Map<Integer, String> CLASS_MAP = Map.of(1, "IN");
    private static final Map<Integer, String> OPCODE_MAP = Map.of(
            0, "QUERY",
            1, "IQUERY",
            2, "STATUS",
            4, "NOTIFY",
            5, "UPDATE"
    );
    private static final Map<Integer, String> RCODE_MAP = Map.of(
            0, "NOERROR",
            1, "FORMERR",
            2, "SERVFAIL",
            3, "NXDOMAIN",
            4, "NOTIMP",
            5, "REFUSED"
    );

    private final byte[] data;
    private int offset;

    private DnsParser(byte[] data) {
        this.data = data;
        this.offset = 0;
    }

    private int readU8() {
        int value = data[offset] & 0xFF;
        offset += 1;
        return value;
    }

    private int readU16() {
        int value = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
        offset += 2;
        return value;
    }

    private long readU32() {
        long value = ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | ((long) (data[offset + 3] & 0xFF));
        offset += 4;
        return value;
    }

    private NameResult readName(Integer startOffset) {
        int currentOffset = startOffset != null ? startOffset : offset;
        List<String> labels = new ArrayList<>();
        boolean jumped = false;
        int originalOffset = currentOffset;
        Map<Integer, Boolean> seenOffsets = new HashMap<>();

        while (true) {
            if (currentOffset >= data.length) {
                throw new IllegalArgumentException("Offset outside message");
            }
            if (seenOffsets.containsKey(currentOffset)) {
                throw new IllegalArgumentException("Compression pointer loop detected");
            }
            seenOffsets.put(currentOffset, true);

            int length = data[currentOffset] & 0xFF;
            if (length == 0) {
                currentOffset += 1;
                break;
            }
            if ((length & 0xC0) == 0xC0) {
                if (currentOffset + 1 >= data.length) {
                    throw new IllegalArgumentException("Truncated compression pointer");
                }
                int pointer = ((length & 0x3F) << 8) | (data[currentOffset + 1] & 0xFF);
                if (!jumped) {
                    originalOffset = currentOffset + 2;
                    jumped = true;
                }
                currentOffset = pointer;
                continue;
            }

            currentOffset += 1;
            byte[] labelBytes = new byte[length];
            System.arraycopy(data, currentOffset, labelBytes, 0, length);
            labels.add(new String(labelBytes));
            currentOffset += length;
        }

        String name = String.join(".", labels) + ".";
        if (!jumped && startOffset == null) {
            offset = currentOffset;
        } else if (jumped && startOffset == null) {
            offset = originalOffset;
        }
        return new NameResult(name, currentOffset);
    }

    private DNSHeader parseHeader() {
        int ident = readU16();
        int flags = readU16();
        int qdcount = readU16();
        int ancount = readU16();
        int nscount = readU16();
        int arcount = readU16();
        return new DNSHeader(ident, flags, qdcount, ancount, nscount, arcount);
    }

    private Question parseQuestion() {
        NameResult nameResult = readName(null);
        int qtype = readU16();
        int qclass = readU16();
        return new Question(nameResult.name, qtype, qclass);
    }

    private ResourceRecord parseRecord() {
        NameResult nameResult = readName(null);
        int type = readU16();
        int recordClass = readU16();
        long ttl = readU32();
        int rdlength = readU16();
        int rdataOffset = offset;
        byte[] rdata = new byte[rdlength];
        System.arraycopy(data, offset, rdata, 0, rdlength);
        offset += rdlength;
        return new ResourceRecord(nameResult.name, type, recordClass, ttl, rdlength, rdataOffset, rdata);
    }

    private static String formatFlags(int flags) {
        int qr = (flags >> 15) & 1;
        int opcode = (flags >> 11) & 0xF;
        int aa = (flags >> 10) & 1;
        int tc = (flags >> 9) & 1;
        int rd = (flags >> 8) & 1;
        int ra = (flags >> 7) & 1;
        int ad = (flags >> 5) & 1;
        int cd = (flags >> 4) & 1;

        List<String> flagNames = new ArrayList<>();
        if (qr == 1) {
            flagNames.add("qr");
        }
        if (aa == 1) {
            flagNames.add("aa");
        }
        if (tc == 1) {
            flagNames.add("tc");
        }
        if (rd == 1) {
            flagNames.add("rd");
        }
        if (ra == 1) {
            flagNames.add("ra");
        }
        if (ad == 1) {
            flagNames.add("ad");
        }
        if (cd == 1) {
            flagNames.add("cd");
        }

        String headerLine = String.format("opcode: %s, status: %s", OPCODE_MAP.getOrDefault(opcode, String.valueOf(opcode)),
                RCODE_MAP.getOrDefault(flags & 0xF, String.valueOf(flags & 0xF)));
        return String.format("%s;;%s", String.join(" ", flagNames), headerLine);
    }

    private String decodeRdata(ResourceRecord record) throws IOException {
        if (record.type == 1 && record.rdata.length == 4) {
            int[] octets = new int[4];
            for (int i = 0; i < 4; i++) {
                octets[i] = record.rdata[i] & 0xFF;
            }
            return String.format("%d.%d.%d.%d", octets[0], octets[1], octets[2], octets[3]);
        }
        if (record.type == 28 && record.rdata.length == 16) {
            return InetAddress.getByAddress(record.rdata).getHostAddress();
        }
        if (record.type == 5) {
            return readName(record.rdataOffset).name;
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : record.rdata) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder input = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            input.append(line.trim());
        }
        String raw = input.toString();
        if (raw.isEmpty()) {
            System.out.println("Provide a hex-encoded DNS message on stdin.");
            return;
        }
        String hexString = raw.replaceAll("\\s+", "");
        byte[] data = hexToBytes(hexString);

        DnsParser parser = new DnsParser(data);
        DNSHeader header = parser.parseHeader();

        String flagsLine = formatFlags(header.flags);
        String[] parts = flagsLine.split(";;", 2);
        System.out.printf(";; ->>HEADER<<- %s, id: %d%n", parts[1], header.ident);
        System.out.printf(";; flags: %s; QUERY: %d, ANSWER: %d, AUTHORITY: %d, ADDITIONAL: %d%n",
                parts[0], header.qdcount, header.ancount, header.nscount, header.arcount);

        System.out.println("\n;; QUESTION SECTION:");
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < header.qdcount; i++) {
            questions.add(parser.parseQuestion());
        }
        for (Question question : questions) {
            String qtype = TYPE_MAP.getOrDefault(question.type, String.valueOf(question.type));
            String qclass = CLASS_MAP.getOrDefault(question.qclass, String.valueOf(question.qclass));
            System.out.printf(";%s\t\t%s\t%s%n", question.name, qclass, qtype);
        }

        System.out.println("\n;; ANSWER SECTION:");
        List<ResourceRecord> answers = new ArrayList<>();
        for (int i = 0; i < header.ancount; i++) {
            answers.add(parser.parseRecord());
        }
        for (ResourceRecord record : answers) {
            String rtype = TYPE_MAP.getOrDefault(record.type, String.valueOf(record.type));
            String rclass = CLASS_MAP.getOrDefault(record.rclass, String.valueOf(record.rclass));
            String rdata = parser.decodeRdata(record);
            System.out.printf("%s\t%d\t%s\t%s\t%s%n", record.name, record.ttl, rclass, rtype, rdata);
        }
    }

    private static class DNSHeader {
        private final int ident;
        private final int flags;
        private final int qdcount;
        private final int ancount;
        private final int nscount;
        private final int arcount;

        private DNSHeader(int ident, int flags, int qdcount, int ancount, int nscount, int arcount) {
            this.ident = ident;
            this.flags = flags;
            this.qdcount = qdcount;
            this.ancount = ancount;
            this.nscount = nscount;
            this.arcount = arcount;
        }
    }

    private static class NameResult {
        private final String name;
        private final int offset;

        private NameResult(String name, int offset) {
            this.name = name;
            this.offset = offset;
        }
    }

    private static class Question {
        private final String name;
        private final int type;
        private final int qclass;

        private Question(String name, int type, int qclass) {
            this.name = name;
            this.type = type;
            this.qclass = qclass;
        }
    }

    private static class ResourceRecord {
        private final String name;
        private final int type;
        private final int rclass;
        private final long ttl;
        private final int rdlength;
        private final int rdataOffset;
        private final byte[] rdata;

        private ResourceRecord(String name, int type, int rclass, long ttl, int rdlength, int rdataOffset, byte[] rdata) {
            this.name = name;
            this.type = type;
            this.rclass = rclass;
            this.ttl = ttl;
            this.rdlength = rdlength;
            this.rdataOffset = rdataOffset;
            this.rdata = rdata;
        }
    }
}
