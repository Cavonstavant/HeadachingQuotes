(function () {
  "use strict";

  var quoteText = document.getElementById("quote-text");
  var quoteCredit = document.getElementById("quote-credit");
  var shuffleBtn = document.getElementById("shuffle-btn");
  var dailyLabel = document.getElementById("daily-label");

  var quotes = window.QUOTES_DATA || [];
  var isDaily = true;

  /**
   * Simple deterministic hash from a string.
   * Used to pick the "quote of the day" consistently.
   */
  function hashString(str) {
    var hash = 0;
    for (var i = 0; i < str.length; i++) {
      var char = str.charCodeAt(i);
      hash = (hash << 5) - hash + char;
      hash |= 0;
    }
    return Math.abs(hash);
  }

  /**
   * Get today's date string in YYYY-MM-DD format (UTC).
   */
  function todayString() {
    var d = new Date();
    return d.getUTCFullYear() + "-" +
      String(d.getUTCMonth() + 1).padStart(2, "0") + "-" +
      String(d.getUTCDate()).padStart(2, "0");
  }

  /**
   * Get the daily quote index (deterministic, same for all visitors).
   */
  function getDailyIndex() {
    return hashString(todayString()) % quotes.length;
  }

  /**
   * Get a random index (different from current if possible).
   */
  function getRandomIndex(currentText) {
    if (quotes.length <= 1) return 0;
    var idx;
    var attempts = 0;
    do {
      idx = Math.floor(Math.random() * quotes.length);
      attempts++;
    } while (quotes[idx].text === currentText && attempts < 20);
    return idx;
  }

  /**
   * Display a quote with a fade-in animation.
   */
  function displayQuote(index) {
    var quote = quotes[index];
    if (!quote) return;

    // Fade out
    quoteText.classList.remove("visible");
    quoteCredit.classList.remove("visible");

    setTimeout(function () {
      quoteText.textContent = "\u201C" + quote.text + "\u201D";
      quoteCredit.textContent = quote.song + " \u2014 " + quote.artist;

      if (quote.album && quote.album !== "Single") {
        quoteCredit.textContent += " (" + quote.album + ")";
      }

      requestAnimationFrame(function () {
        quoteText.classList.add("visible");
        quoteCredit.classList.add("visible");
      });
    }, 300);
  }

  /**
   * Shuffle button handler.
   */
  function onShuffle() {
    isDaily = false;
    dailyLabel.textContent = "random";
    var currentText = quoteText.textContent.slice(1, -1);
    displayQuote(getRandomIndex(currentText));
  }

  // Init
  if (quotes.length === 0) {
    quoteText.textContent = "No quotes available yet.";
    quoteText.classList.add("visible");
  } else {
    displayQuote(getDailyIndex());
    shuffleBtn.addEventListener("click", onShuffle);
  }

  // Keyboard shortcut: Space to shuffle
  document.addEventListener("keydown", function (e) {
    if (e.key === " " && e.target === document.body) {
      e.preventDefault();
      onShuffle();
    }
  });
})();
