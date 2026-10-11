# Hyperframes Composition Brief: FIND-MISSING-PEP

## Objective
Create a short launch-style brag video for FIND-MISSING-PEP — an edge-native AI video surveillance platform that turns existing CCTV into a missing person detection network.

## Output
- Composition directory: `brag-output-2026-09-30-143022/composition/`
- Rendered video: `brag-output-2026-09-30-143022/brag.mp4`
- Format: landscape — 1920x1080
- Duration: 20 seconds

## Source Material
- Project root: `C:\SSD WINDOW\code\FIND -MISSING PEP\`
- Primary files read:
  - `PLAN.md` (mission statement, architecture, UI specs)
  - `backend/app/main.py` (FastAPI backend entrypoint)
  - `web_app/src/index.css` (web app visual identity)
  - `edge_agent/ui/main_window.py` (Edge Agent desktop UI)
  - `edge_agent/ui/camera_feed_widget.py` (camera grid with bounding boxes)
  - `edge_agent/ui/alert_widget.py` (biometric alert popup with side-by-side comparison)
  - `edge_agent/ui/timeline_widget.py` (chronological sighting timeline)
- Product name: FIND-MISSING-PEP
- Tagline / strongest claim: "Temporally verifies matches across consecutive observations (N=3, 5s window, ≥0.60 cosine similarity) to eliminate false alarms. Runs on CPU. No GPU required."
- Key UI or visual moment to recreate: Edge Agent 2×2 camera grid with live RTSP feeds, green/amber/red bounding box overlays, HUD badges; AlertWidget side-by-side comparison with similarity gauge; TimelineWidget reverse-chronological sighting feed; MapTimelinePage with breadcrumb map.
- Copy that must appear verbatim:
  - `RTSP feed → SCRFD detect → ByteTrack track → ArcFace embed → FAISS search → Temporal verify (N=3, 5s, ≥0.60) → ALERT`
  - `Your cameras. Their faces. Found.`
  - `Runs on CPU. No GPU required.`

## Creative Direction
- Tone preset: polished
- Creative direction: quiet premium product film
- Interpretation: Confident restraint. Fewer scenes, longer holds. No jokes. The product's capability speaks. Motion is deliberate. Typography is precise. Audio is a warm bed with professional SFX only at moment of confirmation.
- Angle: "Your existing CCTV cameras just became a missing person detection network." Not a cloud service you upload to — an edge agent that runs locally on a standard CPU, processes 15 FPS video, and only phones home when it's confident.
- Hook: Black screen. Monospace pipeline types out character-by-character. Each arrow arrives on a beat. Final "ALERT" flashes red with impact.
- Outro / punchline: System tray: "FIND-MISSING-PEP — Edge Agent Running." Small text: "Runs on CPU. No GPU required." Logo + "Your cameras. Their faces. Found."
- Avoid:
  - Generic SaaS language ("streamline your workflow", "seamless integration")
  - Abstract filler visuals (particles, gradients, generic motion graphics)
  - Unrelated visual redesign — use the project's actual dark mode palette and UI components

## Visual Identity
- Background: #09090b (Edge Agent dark) / #fdfdfc (Web app light)
- Text: #f4f4f5 (dark mode) / #17181a (light mode)
- Accent: #0e6f8a (teal/cyan) — primary brand
- Accent Hi: #128aa9
- Success: #10b981 (confirmed match green)
- Warning: #f59e0b (candidate amber)
- Error: #ef4444 (alert red)
- Display font: System UI stack (-apple-system, BlinkMacSystemFont, 'Segoe UI', Inter, Roboto)
- Body font: Same
- Visual references from the project:
  - CameraFeedWidget paintEvent: 2×2 grid, letterboxed video, bounding boxes with rounded corners, label pills, HUD channel badge + status dot, FPS badge
  - AlertWidget: side-by-side comparison group (180×180 photos), "VS" divider, similarity gauge (QProgressBar), metadata rows, Confirm/Reject buttons
  - TimelineWidget: QTableWidget with Time/Person/Camera/Score/Status columns, green/amber/red score coloring, row double-click → evidence inspector
  - StatusBarWidget: aggregate FPS, faces detected, backend online/offline, last sync time

## Storyboard
Use the storyboard in `brag-output-2026-09-30-143022/brag-plan.md` as the creative contract.

Scene summary:
1. Pipeline Hook — 3.0s — Monospace pipeline types out; "ALERT" flashes red
2. Edge Agent Camera Grid — 5.0s — 2×2 grid, CAM-01 live, box turns amber then red CONFIRMED
3. Alert Popup Comparison — 4.5s — Side-by-side registered vs live crop, gauge fills to 87%, metadata rows appear
4. Timeline & Dashboard — 4.5s — Timeline row inserts, map markers pulse, click → forensic viewer
5. Outro / Logo — 3.0s — System tray status, logo mark + wordmark + tagline reveal

## Audio
- Audio role: warm bed / sparse professional accents
- Audio arc: Quiet warm bed throughout. Mechanical ticks on pipeline arrows. Soft impact on amber→red transition. Card slide on "VS" divider. Bell on gauge fill. Quiet clicks on timeline inserts. Bell on logo. Music fades under final 2s.
- Music: happy-beats-business-moves-vol-1-by-ende-dot-app.mp3 (120.19 BPM, corporate warm)
- Music treatment: Start at 0:00, volume -18dB bed, fade in 1s, fade out last 2s. Target strong cues at 17.02s (Scene 3 alert reveal), 20.02s (Scene 4 timeline insert), 23.02s (Scene 5 logo).
- Music cue guidance: Bundled preset available at `.agents/skills/brag/assets/music/cues/happy-beats-business-moves-vol-1-by-ende-dot-app.music-cues.json`. Strong cues: 17.02s, 17.52s, 18.52s, 20.02s, 23.02s, 23.52s. Beat grid every ~0.5s (3.02, 3.52, 4.02...). Lock Scene 3 alert popup reveal to 17.02s ±0.15s. Lock Scene 4 first timeline row to 20.02s ±0.15s. Lock Scene 5 logo to 23.02s ±0.15s. Sequential metadata rows in Scene 3 snap to every other beat (allowing read time).
- Audio-reactive treatment: subtle; use music RMS to make the confirmation badge glow breathe in Scene 2, and the similarity gauge track presence in Scene 3. No waveform/equalizer visuals.
- Audio-coupled moments:
  - Scene 1 pipeline arrows — typing animation with subtle key ticks (per arrow)
  - Scene 2 amber→red box transition — soft impact
  - Scene 3 "VS" divider appearance — card slide
  - Scene 3 similarity gauge fill — soft bell
  - Scene 3 metadata rows sequential reveal — quiet clicks
  - Scene 4 timeline row inserts — quiet clicks
  - Scene 4 map marker pulse + click simulation — click
  - Scene 5 logo + wordmark + tagline sequential reveal — clicks + final bell
- SFX selection guidance: Use lower high-frequency-risk sounds for repeated/polished moments. From sfx-analysis.md: button press → interface/click_003.ogg (low risk); card reveal → casino/card-slide-1.ogg (medium risk); success/bell → impact/impactBell_heavy_000.ogg (medium risk); major reveal/transition → impact/impactSoft_medium_001.ogg (low risk). Avoid high-risk SFX (bright/high-frequency) for repeated ticks.
- SFX analysis guidance: `.agents/skills/brag/assets/sfx/sfx-analysis.md` and `.agents/skills/brag/assets/sfx/sfx-analysis.json`
- Exact SFX choice: Hyperframes should choose filenames, timestamps, density, and volume based on the implemented animation.
- Audio files: Music copied to `composition/assets/music/`. Hyperframes will copy selected SFX into `composition/assets/sfx/`.

## Hyperframes Instructions
Load the composition-building Hyperframes domain skills — `hyperframes-core` (composition contract + `data-*` timing), `hyperframes-animation` (motion), `hyperframes-creative` (design spec, beats, audio-reactive), `hyperframes-keyframes` (seek-safe keyframes), and `hyperframes-cli` (lint/check/render). /brag is its own workflow: do not enter the `hyperframes` entry-point intent interview and do not route into its generic promo / launch-video workflow. Prefer native Hyperframes conventions over anything in `/brag`.

Requirements:
- Show at least one real UI, copy, or visual element from the source project.
- Keep all text readable in the final render (reading floor: short label ~0.8s settled; sentence ~0.3s/word).
- Keep the video within 15-25 seconds (target 20s).
- Include the planned music/SFX layer.
- Treat `/brag` audio notes as guidance, not a fixed cue sheet. Choose SFX after the visual animation exists.
- Treat music cue metadata as optional timing hints. Hyperframes decides exact animation timing and should ignore cues that hurt readability, scene pacing, or the product story.
- Major reveals may move toward nearby strong cues within about 0.15s. Smaller entrances may align to nearby beat points within about 0.10s. Use only 1-3 strong cue locks in a 15-25s video unless the edit clearly benefits from more.
- Use SFX to support motion and interaction: card sounds for card-like reveals, short announcement cues for major payoffs, key/click sounds for text or user actions, and restraint when the edit is already busy.
- Honor planned music treatment such as fade-outs, ducking, beat-aligned reveals, or letting a final SFX ring over the music, using the best Hyperframes-supported implementation.
- When music is present and the treatment is not `none`, consider Hyperframes audio-reactive workflow: extract audio data and use RMS/frequency bands for subtle, brand-specific motion. Good targets are glow, depth, background warmth, card presence, title emphasis, or other existing visual elements. Avoid waveform/equalizer visuals, musical-note graphics, generic particle systems, strobing, or heavy pulsing.
- Use local assets for audio and any required runtime/media dependencies when possible.
- Run `hyperframes check` before render — it is brag's single gate.