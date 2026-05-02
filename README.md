# WaveBridge Android

WaveBridge Android is the phone-side receiver for WaveBridge, a small for-fun project that streams Windows PC audio to a phone over the local network.

The idea came from a simple personal annoyance: the headphones were already connected to the phone, while the PC had no convenient audio setup. WaveBridge turns that phone into a lightweight LAN audio receiver so PC audio can be heard through whatever is connected to the phone.

This repository contains the Android receiver. The matching Windows sender lives here:

https://github.com/Markussiin/WaveBridge

## Status

The first working receiver path is implemented:

- listens for WaveBridge UDP discovery on port `37020`
- replies as a phone receiver with PCM support, plus Opus when a device decoder is available
- receives audio packets on UDP port `37021`
- parses the `PSNK` binary packet header manually
- reassembles chunked frames
- runs as a foreground media-playback service while active
- holds Wi-Fi/multicast/CPU locks only while receiving, then releases them on stop
- includes one-tap audio presets for low latency, balanced listening, stability, and battery saving
- exposes custom latency, buffer, playback, route, Opus, and power settings
- adds optional audio effects for bass boost, loudness, virtualizer, and five-band EQ tuning
- uses a small jitter buffer to smooth UDP timing
- fills missing PCM frames with silence instead of letting playback timing drift
- trims the buffer if the phone falls behind, keeping latency bounded
- rebuilds `AudioTrack` when the phone audio route changes
- replies to sender Ping packets with Pong keepalives
- plays 48 kHz stereo PCM S16LE through `AudioTrack`
- shows receiver state, sender address, route, power mode, buffer, packet/frame, loss, underrun, and drift stats

Opus decoding is experimental and depends on Android `MediaCodec` support on the device. Use the Windows sender with the default PCM path first:

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

## Audio Presets

The receiver has four built-in profiles:

- `Low latency`: smaller buffers for interactive audio and gaming.
- `Balanced`: default profile for stable Wi-Fi and everyday listening.
- `Studio`: larger buffers for the smoothest playback on weaker networks.
- `Battery saver`: less aggressive Wi-Fi/audio behavior for longer sessions.

The `Quality`, `Power`, and `Advanced` tabs let you tune the same settings manually:

- start buffer
- maximum latency before trimming old frames
- Android `AudioTrack` buffer size
- low-latency output mode
- silence fill for missing frames
- latency trim
- route recovery
- Opus advertisement when a device decoder is available
- Wi-Fi low-latency lock
- CPU wake lock
- notification update frequency

## Audio Effects

The `Effects` tab is optional and defaults to a clean signal. It uses Android's built-in audio effects on the active `AudioTrack` session, then releases them when disabled so the receiver stays lightweight.

Available controls:

- presets: `Flat`, `Bass boost`, `Warm`, `Bright`, `Voice`, and `Movie`
- manual bass boost strength
- loudness gain
- virtualizer strength, when supported by the device
- five-band equalizer: low, low mid, mid, high mid, and high

Device support varies because these effects are implemented by Android and the phone vendor. If a specific effect is unavailable, WaveBridge skips it and keeps playback running.

## Protocol Compatibility

This app expects the current WaveBridge packet format:

- discovery: UTF-8 JSON over UDP
- audio: manually serialized `PSNK` binary UDP packets
- codec: PCM S16, plus experimental Opus when device decoding is available
- sample rate: 48 kHz
- channels: stereo
- default audio port: `37021`
- default discovery port: `37020`

The app handles Start, Stop, Ping, and Pong control packets. Ping/Pong keeps the sender and receiver aware of each other without a noisy polling loop.

## Troubleshooting

- If the phone does not appear, make sure the PC and phone are on the same network.
- Disable guest Wi-Fi/client isolation if your router has it enabled.
- Keep the app running with the foreground notification while testing.
- Use `--debug` on the Windows sender to confirm discovery and packet flow.
- Use PCM first; Opus support depends on the device decoder and may need more tuning.

## Roadmap

- Tune Opus decoding across more Android devices.
- Add user-adjustable latency presets.
- Improve latency controls and diagnostics.
- Add a cleaner package/application id before publishing.

## License

Licensed under the [Apache License 2.0](LICENSE).

Redistributions and derivative works must retain the attribution notice in
[NOTICE](NOTICE), which credits Markuss Kruze as the original creator.
