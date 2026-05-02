# WaveBridge Android

WaveBridge Android is the phone-side receiver for WaveBridge, a small for-fun project that streams Windows PC audio to a phone over the local network.

The idea came from a simple personal annoyance: the headphones were already connected to the phone, while the PC had no convenient audio setup. WaveBridge turns that phone into a lightweight LAN audio receiver so PC audio can be heard through whatever is connected to the phone.

This repository contains the Android receiver. The matching Windows sender lives here:

https://github.com/Markussiin/WaveBridge

## Status

The first working receiver path is implemented:

- listens for WaveBridge UDP discovery on port `37020`
- replies as a phone receiver with PCM support
- receives audio packets on UDP port `37021`
- parses the `PSNK` binary packet header manually
- reassembles chunked frames
- plays 48 kHz stereo PCM S16LE through `AudioTrack`
- shows receiver state, sender address, packet/frame counters, invalid packet count, and sequence gap count

Opus is not implemented on Android yet. Use the Windows sender with the default PCM path:

```powershell
WaveBridge.exe send --codec pcm --debug
```

## Requirements

- Android Studio
- Android device on the same Wi-Fi/LAN as the Windows PC
- Android SDK configured by Android Studio
- Windows WaveBridge sender running on the PC

The receiver uses UDP broadcast discovery. Some networks block broadcast or isolate Wi-Fi clients, especially guest networks and some public/shared access points.

## Build

Open this project in Android Studio and run the `app` configuration on a connected device.

From a terminal with Java available:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Usage

1. Install and open the Android app.
2. Tap `Start`.
3. On the Windows PC, run the sender:

```powershell
WaveBridge.exe send --codec pcm --debug
```

4. Select the phone when it appears.
5. Play audio on the PC.

The app should switch to `Playing` and the packet/frame counters should start increasing.

## Protocol Compatibility

This app expects the current WaveBridge packet format:

- discovery: UTF-8 JSON over UDP
- audio: manually serialized `PSNK` binary UDP packets
- codec: PCM S16 only for now
- sample rate: 48 kHz
- channels: stereo
- default audio port: `37021`
- default discovery port: `37020`

The protocol already has packet types for future control messages such as start, stop, ping, and pong, but the current Android runtime focuses on discovery and audio playback.

## Troubleshooting

- If the phone does not appear, make sure the PC and phone are on the same network.
- Disable guest Wi-Fi/client isolation if your router has it enabled.
- Keep the phone screen awake while testing.
- Use `--debug` on the Windows sender to confirm discovery and packet flow.
- Use PCM first; Opus is planned but not ready on Android.

## Roadmap

- Add Opus decoding.
- Add jitter buffer tuning and better packet loss handling.
- Add start/stop/ping/pong control packet handling.
- Add foreground service mode so playback can continue with the screen off.
- Improve latency controls and diagnostics.
- Add a cleaner package/application id before publishing.

## License

No license has been selected yet. Treat the code as source-available until a license is added.
