# Brag Plan: FIND-MISSING-PEP

## What is this app?
An installable, edge-native AI video surveillance platform that connects to existing CCTV infrastructure, continuously detects and tracks faces from live RTSP feeds, matches them against active missing-person reference galleries in sub-second time, temporally verifies matches across consecutive observations (3 sightings within 5 seconds at ≥0.60 cosine similarity) to eliminate false alarms, captures tamper-evident forensic evidence packages (face crop, full frame, 5-second video clip), and dispatches instantaneous alerts via SSE and mobile push.

## The angle
"Your existing CCTV cameras just became a missing person detection network." Not a cloud service you upload to — an edge agent that runs locally on a standard CPU, processes 15 FPS video, and only phones home when it's confident. The hook is the tension between "this runs on your hardware" and "it does biometric verification at 0.60 cosine similarity with temporal confirmation."

## Hook (first 2-3 seconds)
Black screen. A single line types out, character by character:
```
CCTV feed → Face detected → Embedding extracted → FAISS search → Temporal verify (3/5s) → ALERT
```
Each arrow arrives on a subtle beat. The final "ALERT" hits in flashing red with a low impact sound. No logo. No product name yet. Just the pipeline.

## Key moments (the middle)
- **The Edge Agent desktop**: 2×2 camera grid rendering live RTSP feeds at 15 FPS. Green bounding boxes track faces. One box turns amber (candidate ≥0.50). Then flashes red with "CONFIRMED 87%" — the temporal verifier triggered.
- **The Alert Popup**: Side-by-side comparison. Left: Registered missing person photo. Right: Live CCTV face crop. Center: "VS" divider. Bottom: Similarity gauge filling to 87%, FIR badge, reporter contact. Confirm/Reject buttons.
- **The Timeline**: Reverse-chronological sighting feed. New entry slides in at top: "14:32:11 | Priya Sharma (a1b2c3d4) | CAM-01 | 87.3% | CONFIRMED". Green text. Auto-scrolls.
- **The Web Dashboard**: Map timeline with breadcrumb sightings. Camera markers on Leaflet map. Click a stop → forensic split-screen viewer opens. Real data, not mockups.

## Outro / punchline
Cut to the Edge Agent system tray. "FIND-MISSING-PEP — Edge Agent Running." Small text: "Runs on CPU. No GPU required."
Final frame: Logo mark + "Your cameras. Their faces. Found."

## User flow worth showing
**Entry** → Configure RTSP camera in Edge Agent (Camera Config dialog)  
**Key Action** → System processes feed: SCRFD detects → ByteTrack tracks → ArcFace extracts → FAISS searches → Temporal verifier confirms (N=3, 5s window, ≥0.60)  
**Result** → Alert popup appears with side-by-side comparison → Evidence auto-captured (crop + frame + 5s clip) → Timeline updates → Background upload to backend → Web dashboard map updates in real-time via SSE

## Tone
- Preset: polished
- Creative direction: quiet premium product film
- Interpretation: Confident restraint. Fewer scenes, longer holds. No jokes. The product's capability speaks. Motion is deliberate. Typography is precise. Audio is a warm bed with professional SFX only at moment of confirmation.

## Format: landscape — 1920x1080
## Duration: 20 seconds

## Visual identity (from the project)
- Background: #09090b (Edge Agent dark) / #fdfdfc (Web app light)
- Accent: #0e6f8a (teal/cyan) — primary brand
- Accent Hi: #128aa9
- Success: #10b981 (confirmed match green)
- Warning: #f59e0b (candidate amber)
- Error: #ef4444 (alert red)
- Text: #f4f4f5 (dark mode) / #17181a (light mode)
- Display font: System UI stack (-apple-system, BlinkMacSystemFont, 'Segoe UI', Inter, Roboto)
- Body font: Same
- Strongest visual element: The 2×2 camera grid with live bounding box overlays and HUD badges; the side-by-side alert comparison card

## Share copy (draft)
Your CCTV just became a missing person finder. Edge AI that runs on CPU, verifies in 5 seconds, zero false alarms.

## Audio direction
- Role: warm bed / sparse professional accents
- Music: happy-beats-business-moves-vol-1-by-ende-dot-app.mp3 (120 BPM, corporate warm)
- Music treatment: Start at 0:00, volume -18dB bed, fade in over 1s, fade out last 2s. Target strong cues at 17.02s (major reveal), 20.02s (timeline payoff), 23.02s (outro logo).
- Music cue guidance: Preset available. Strong cues at 17.02s, 17.52s, 18.52s, 20.02s, 23.02s, 23.52s. Beat grid every ~0.5s. Use 17.02s for Alert Popup reveal, 20.02s for Timeline entry slide-in, 23.02s for logo payoff.
- Audio-reactive treatment: subtle; use music RMS to make the confirmation badge glow breathe, not waveform bars.
- SFX posture: sparse; motion-matched; professional restraint
- Audio-coupled moments: 
  - Hook pipeline text: subtle keyboard tick per arrow (interface/click_003.ogg)
  - Amber→Red box transition: soft impact (impact/impactSoft_medium_001.ogg)
  - Alert popup "VS" divider appearance: card slide (casino/card-slide-1.ogg)
  - Similarity gauge fill: soft bell (impact/impactBell_heavy_000.ogg)
  - Timeline row insert: quiet click (interface/click_002.ogg)
  - Logo mark final: bell heavy (impact/impactBell_heavy_003.ogg)
- Restraint rule: No SFX on every cut. Only at moments of state change (candidate→confirmed, popup open, gauge fill, row insert, logo). Music bed stays quiet underneath.

## Storyboard

### Scene 1 — Pipeline Hook — 3.0s
Black background. Monospace text types out character-by-character:
```
RTSP feed → SCRFD detect → ByteTrack track → ArcFace embed → FAISS search → Temporal verify (N=3, 5s, ≥0.60) → ALERT
```
Each "→" arrives on a beat (~0.5s apart). "ALERT" flashes red (#ef4444) with low impact hit. Hold 1s on final line.
Sequential/interaction: yes — typed pipeline, each arrow + stage reveals sequentially
Audio intent: tension building, mechanical precision
Audio-coupled idea: typed text with subtle key ticks per character; each arrow gets a soft click
Music: warm bed starts at 0:00, -18dB
Transition mood: hard cut → Scene 2

### Scene 2 — Edge Agent Camera Grid — 5.0s
Fade in: Edge Agent MainWindow. 2×2 camera grid (CAM-01 to CAM-04). CAM-01 shows live feed (test_cctv_feed.mp4). Green bounding boxes with "ID #1", "ID #2" labels. Top-left HUD pills: "CAM-01 — Entrance North" with green status dot. Top-right: "14.8 FPS" cyan badge.
At ~1.5s: One box on CAM-01 turns amber (#f59e0b), label updates "Priya S. (62%)". Soft impact SFX.
At ~3.0s: Same box flashes red (#ef4444), label "Priya Sharma (87%) CONFIRMED". Stronger impact SFX. Red pulsing glow on box.
Hold 2s on confirmed state.
Sequential/interaction: no — continuous live feed simulation
Audio intent: calm monitoring → sudden confirmation
Audio-coupled idea: amber transition = soft impact; red flash = heavier impact + music cue at 17.02s
Music: bed continues, subtle RMS glow on red box
Transition mood: hard cut → Scene 3

### Scene 3 — Alert Popup Comparison — 4.5s
AlertWidget slides up from bottom (modal). Dark dialog (#141417). Banner: "🚨 SIGHTING CONFIRMED — AUTO-VERIFIED BY EDGE AGENT" in green banner (#065f46).
Center: Side-by-side comparison group. Left: "Registered Photo" — reference photo of missing person. Center: "VS" divider (large, weighted). Right: "Live CCTV Detection" — face crop from CAM-01. Both photos 180×180, rounded corners, accent borders.
Below: Similarity gauge — green progress bar filling to 87%. "Confidence: 87.3% Match". Bell SFX at fill completion.
Metadata rows appear sequentially (0.3s apart): Person name + Case ID → Reporter + Contact → Camera + Time → FIR Verified badge.
Confirm/Reject buttons visible but not interacted.
Sequential/interaction: yes — metadata rows appear one by one; gauge fills animated
Audio intent: forensic gravity, confirmation weight
Audio-coupled idea: "VS" divider = card slide; gauge fill = soft bell; each metadata row = quiet click
Music: bed continues, target 20.02s strong cue for gauge fill completion
Transition mood: clean crossfade → Scene 4

### Scene 4 — Timeline & Dashboard — 4.5s
Split view: Left 60% TimelineWidget, Right 40% MapTimelinePage (Leaflet map).
Timeline: New row slides in at top — "14:32:11 | Priya Sharma (a1b2c3) | CAM-01 | 87.3% | CONFIRMED" in green. Row highlight fades.
Map: Camera marker at CAM-01 location pulses red. Click → forensic split-screen viewer opens (comparison-card component). Shows registered vs live crop with similarity gauge. Close → map returns.
Second sighting row slides in: "14:31:58 | Priya Sharma (a1b2c3) | CAM-02 | 76.1% | CONFIRMED". Map adds second marker, draws directional vector between CAM-01 → CAM-02.
Sequential/interaction: yes — timeline rows insert sequentially; map marker pulse + click simulation
Audio intent: accumulation, pattern emerging
Audio-coupled idea: each timeline row insert = quiet click; map pulse = subtle; click = click SFX
Music: bed continues, target 23.02s strong cue for second row
Transition mood: slow crossfade → Scene 5

### Scene 5 — Outro / Logo — 3.0s
System tray / taskbar area. Small window: "FIND-MISSING-PEP — Edge Agent Running ●" with green status dot.
Below: "Runs on CPU. No GPU required." in muted text.
Logo mark (34×34 rounded square, dark bg, white icon) + wordmark "FIND-MISSING-PEP" appears with subtle scale-in.
Tagline: "Your cameras. Their faces. Found."
Hold 1.5s.
Sequential/interaction: yes — logo + wordmark + tagline reveal sequentially (0.4s each)
Audio intent: quiet confidence, closure
Audio-coupled idea: each line = quiet click; logo = bell heavy
Music: fade out over last 2s, ends on 23.52s cue
Transition mood: fade to black

**Music mood for this video:** warm corporate, confident, understated
**Audio summary:** Warm 120 BPM bed runs throughout at -18dB. Sparse professional SFX only at state changes: pipeline arrows (keyboard ticks), amber→red transition (soft impact), alert popup (card slide + bell), timeline inserts (clicks), logo (bell). Music strong cues at 17.02s (alert reveal), 20.02s (gauge fill), 23.02s (logo) guide major visual moments. No audio-reactive flashiness — just subtle RMS glow on confirmation elements.