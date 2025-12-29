# AirLiftOne Marketing Site

A single-page marketing site for AirLiftOne, an autonomous airline delivery service for groceries, restaurant meals, pharmacy items, returns, and more.

## Quick link
Serve the static files from the project root, then open the page locally:

```bash
# from the achanta1 directory
python -m http.server 8000
# or, if Python 3 is installed as python3
python3 -m http.server 8000
```

Then visit [http://localhost:8000/index.html](http://localhost:8000/index.html) in your browser (not a remote device).

Prefer one command? Run the included helper to start a server and open your default browser automatically:

```bash
python serve.py
```

Options if 8000 is busy or you do not want it to open your browser:

```bash
# pick a different port
python serve.py --port 8080

# skip auto-opening the browser
python serve.py --no-browser

# serve briefly (e.g., CI checks)
python serve.py --duration 5 --no-browser
```

**If you still see “site can’t be reached”:**
- Confirm the server is running in the same terminal (you should see log lines when you refresh the page).
- Make sure you are in the `achanta1` folder before starting the server—`index.html`, `styles.css`, and `script.js` must be in the same directory where you run the command.
- Try a different port if 8000 is already used locally: `python -m http.server 8080` and open http://localhost:8080/index.html.
- As a fallback, you can open `index.html` directly in your browser via the file system (double-click or drag the file in).

## Running locally
Open `index.html` in your browser (no build tools required). The site is fully static and styled with `styles.css`; `script.js` powers the mobile navigation toggle.

## Sections
- **Hero:** headline, primary calls-to-action, and key delivery capability pills.
- **Delivery verticals:** four-card grid for groceries, prepared meals, pharmacy & care, and retail/returns.
- **Operations console:** highlights dispatch, telemetry, and customer messaging with inline reliability metrics.
- **Flight network timeline:** three-step walkthrough of dispatch, airborne delivery, and precision drop.
- **Safety & compliance:** cards detailing avionics redundancy, pad standards, and compliance reporting.
- **Coverage:** stylized map grid for hubs and certified pads.
- **FAQ:** common partner launch and integration questions.
- **Call to action:** prompt to book a discovery flight or explore services.
