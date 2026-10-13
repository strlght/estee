# Estee

A tiny Android app that takes audio files from the **share sheet** and sends them to a
[whisper.cpp server](https://github.com/ggml-org/whisper.cpp/tree/master/examples/server)
for transcription.

## Install

<p align="left">
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/strlght/estee/">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="img/badge/badge-obtainium.webp" height="60">
      <img alt="Get it on Obtainium" src="img/badge/badge-obtainium.webp" height="60">
    </picture>
  </a>
  <a href="https://github.com/strlght/estee/releases">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="img/badge/badge-github.webp" height="60">
      <img alt="Get it on GitHub" src="img/badge/badge-github.webp" height="60">
    </picture>
  </a>
</p>

### How to verify

Package ID: `io.strlght.estee`

SHA-256 of signing certificate: `78:85:4A:22:ED:50:F0:BE:EF:65:43:BB:12:73:77:DD:DE:CA:F8:A8:4C:A5:21:13:5A:C1:32:1E:50:81:D5:DF`

## Usage

Set your whisper.cpp server endpoint in **Settings** (e.g. `http://192.168.1.50:8080/inference`),
then share any audio file to Estee. The app shows upload progress, then an elapsed timer while
the server runs inference, and the transcript is fully selectable with Copy / Share actions.
