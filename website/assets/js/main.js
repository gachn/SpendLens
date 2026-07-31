/* SpendLens site — nav, scroll reveal, footer year, support form */
(function () {
  "use strict";

  // ----- Mobile nav toggle -----
  var toggle = document.querySelector(".nav-toggle");
  var links = document.querySelector(".nav-links");
  if (toggle && links) {
    toggle.addEventListener("click", function () {
      var open = links.classList.toggle("open");
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
    });
    // Close menu when a link is clicked (mobile)
    links.addEventListener("click", function (e) {
      if (e.target.closest("a")) {
        links.classList.remove("open");
        toggle.setAttribute("aria-expanded", "false");
      }
    });
  }

  // ----- Footer year -----
  var yr = document.getElementById("yr");
  if (yr) yr.textContent = new Date().getFullYear();

  // ----- Scroll reveal -----
  var reveals = document.querySelectorAll(".reveal");
  if ("IntersectionObserver" in window && reveals.length) {
    var io = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) {
            entry.target.classList.add("in");
            io.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: "0px 0px -40px 0px" }
    );
    reveals.forEach(function (el, i) {
      el.style.transitionDelay = (i % 4) * 70 + "ms";
      io.observe(el);
    });
  } else {
    reveals.forEach(function (el) { el.classList.add("in"); });
  }

  // ----- Support form (client-only demo; no backend) -----
  var form = document.getElementById("support-form");
  if (form) {
    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var status = document.getElementById("form-status");
      var name = (document.getElementById("name") || {}).value || "there";
      if (status) {
        status.textContent =
          "Thanks, " + name + "! Your message has been prepared. Please also email it to support@spendlens.app to reach us directly.";
        status.classList.add("ok");
      }
      form.reset();
    });
  }
})();
