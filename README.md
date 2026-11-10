# Petit Polyglot

A private language-learning app for the kids' Android tablets. French is the first language pack.

- The game itself is one page: `app/src/main/assets/index.html`.
- `MainActivity.java` shows that page full screen and reads words aloud with the tablet's text-to-speech.
- Every change pushed to `main` builds a new `petit-polyglot.apk` and publishes it under **Releases**.

To install or update: open the latest release on the tablet, tap `petit-polyglot.apk`, and allow the install.
Progress is kept on the tablet and survives updates (do not uninstall the app, that erases it).
