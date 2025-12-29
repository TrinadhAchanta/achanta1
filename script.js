const toggle = document.querySelector('.nav__toggle');
const links = document.querySelector('.nav__links');

if (toggle && links) {
  toggle.addEventListener('click', () => {
    const isOpen = links.classList.toggle('nav__links--open');
    toggle.setAttribute('aria-expanded', String(isOpen));
  });

  links.addEventListener('click', (event) => {
    if (event.target instanceof HTMLElement && event.target.tagName.toLowerCase() === 'a') {
      links.classList.remove('nav__links--open');
      toggle.setAttribute('aria-expanded', 'false');
    }
  });
}
