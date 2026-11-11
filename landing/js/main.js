/**
 * Radar Stock - Script de Interacciones (JavaScript Puro)
 * Ultraligero, sin dependencias externas
 */

document.addEventListener('DOMContentLoaded', () => {
  // 1. Cabecera fija con sombra al hacer scroll
  const header = document.querySelector('.site-header');
  const heroSection = document.querySelector('.hero-section');
  const mobileStickyBar = document.querySelector('.mobile-sticky-bar');

  const onScroll = () => {
    const scrollY = window.scrollY || window.pageYOffset;

    // Sombra en cabecera
    if (header) {
      if (scrollY > 20) {
        header.classList.add('scrolled');
      } else {
        header.classList.remove('scrolled');
      }
    }

    // Barra flotante móvil: solo visible cuando se ha bajado más allá del botón hero
    if (mobileStickyBar && heroSection) {
      const heroBottom = heroSection.getBoundingClientRect().bottom;
      if (heroBottom > 100) {
        // Estamos en el Hero, ocultamos la barra flotante para no duplicar el botón
        mobileStickyBar.classList.add('hide');
      } else {
        // Pasamos el Hero, mostramos la barra para que siempre haya acceso a descargar
        mobileStickyBar.classList.remove('hide');
      }
    }
  };

  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll(); // Estado inicial

  // 2. Copiar correo de contacto al portapapeles
  const btnCopyEmail = document.getElementById('btn-copy-email');
  if (btnCopyEmail) {
    const originalText = btnCopyEmail.innerHTML;
    const emailToCopy = 'contacto@radarstock.shop';

    btnCopyEmail.addEventListener('click', async () => {
      try {
        if (navigator.clipboard && navigator.clipboard.writeText) {
          await navigator.clipboard.writeText(emailToCopy);
        } else {
          // Fallback para navegadores antiguos
          const textarea = document.createElement('textarea');
          textarea.value = emailToCopy;
          textarea.style.position = 'fixed';
          textarea.style.opacity = '0';
          document.body.appendChild(textarea);
          textarea.select();
          document.execCommand('copy');
          document.body.removeChild(textarea);
        }

        btnCopyEmail.innerHTML = `
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
          </svg>
          <span>¡Copiado!</span>
        `;
        btnCopyEmail.style.backgroundColor = '#ECFDF5';
        btnCopyEmail.style.borderColor = '#059669';
        btnCopyEmail.style.color = '#059669';

        setTimeout(() => {
          btnCopyEmail.innerHTML = originalText;
          btnCopyEmail.removeAttribute('style');
        }, 2500);
      } catch (err) {
        // En caso de bloqueo por permisos, fallback silencioso
        window.location.href = `mailto:${emailToCopy}`;
      }
    });
  }

  // 3. Acordeón FAQ: cerrar otros al abrir uno (comportamiento opcional elegante)
  const faqItems = document.querySelectorAll('.faq-item');
  faqItems.forEach((item) => {
    item.addEventListener('toggle', () => {
      if (item.open) {
        faqItems.forEach((other) => {
          if (other !== item && other.open) {
            other.removeAttribute('open');
          }
        });
      }
    });
  });
});

