"""Migrate all local MK Sinha missing person photos and sightings into production."""
import json
import os
import sqlite3
import urllib.request
import uuid
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
PROD_BASE_URL = "http://65.20.80.222:8000"
USER_TOKEN = "mock-token-mksinha77756@gmail.com"
AGENT_API_KEY = "6e_oD7PUWNxHIQ1uwJgunfJqbp0YfU8J2RJWl2HK5jQYKntEChsbsNjC63iiuv86"

PROD_PERSON_MAP = {
    # local missing person ID -> prod missing person UUID
    "bfa41c1fd76e4c93961047474c3325d1": "848355a4-56cc-4942-82bd-c0bb24abfc74",  # asish
    "e0365fc8becc46c091f4e076715d4d5b": "d4226a18-12e1-4f79-9a8e-bd62688188c3",  # manoj (active)
    "fd12bd719df34621b9c68696eea0f282": "d4226a18-12e1-4f79-9a8e-bd62688188c3",  # manoj (found)
}


def upload_additional_photo(person_prod_id: str, photo_path: str):
    """Upload photo to an existing report on production."""
    p = ROOT_DIR / photo_path.lstrip("/\\")
    if not p.exists():
        print(f"  [PHOTO SKIP] File not found: {p}")
        return

    boundary = f"----WebKitFormBoundary{uuid.uuid4().hex}"
    body = bytearray()

    body.extend(f"--{boundary}\r\n".encode())
    body.extend(f'Content-Disposition: form-data; name="file"; filename="{p.name}"\r\n'.encode())
    body.extend(b"Content-Type: image/jpeg\r\n\r\n")
    with open(p, "rb") as f:
        body.extend(f.read())
    body.extend(b"\r\n")

    body.extend(f"--{boundary}\r\n".encode())
    body.extend(b'Content-Disposition: form-data; name="is_primary"\r\n\r\nfalse\r\n')

    body.extend(f"--{boundary}--\r\n".encode())

    req = urllib.request.Request(
        f"{PROD_BASE_URL}/api/reports/{person_prod_id}/photos",
        data=bytes(body),
        headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Authorization": f"Bearer {USER_TOKEN}",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            res = json.loads(r.read().decode())
            print(f"  [PHOTO OK] Added photo {p.name} to {person_prod_id}: photo_id={res.get('id')}")
    except Exception as e:
        print(f"  [PHOTO ERR] Failed to upload {p.name}: {e}")


def upload_sighting(person_prod_id: str, sighting_data: dict) -> bool:
    """Upload a sighting to production."""
    crop_rel = (sighting_data.get("face_crop_path") or "").lstrip("/\\")
    frame_rel = (sighting_data.get("full_frame_path") or "").lstrip("/\\")

    crop_path = ROOT_DIR / crop_rel
    frame_path = ROOT_DIR / frame_rel

    if not crop_path.exists():
        print(f"  [SIGHTING SKIP] Crop missing: {crop_path}")
        return False
    if not frame_path.exists():
        frame_path = crop_path

    boundary = f"----WebKitFormBoundary{uuid.uuid4().hex}"
    body = bytearray()

    detected_at = sighting_data.get("detected_at")
    if isinstance(detected_at, str):
        detected_at = detected_at.replace(" ", "T")
        if not detected_at.endswith("Z") and "+" not in detected_at:
            detected_at += "Z"
    else:
        detected_at = "2026-09-11T14:00:00Z"

    fields = {
        "person_id": person_prod_id,
        "camera_id": "CAM-01",
        "similarity_score": f"{float(sighting_data.get('similarity_score', 0.75)):.4f}",
        "detected_at": detected_at,
        "confidence_level": sighting_data.get("confidence_level") or "CONFIRMED",
        "num_frames_matched": str(sighting_data.get("num_frames_matched") or 4),
        "camera_location": sighting_data.get("camera_location") or "Main Building",
    }

    for k, v in fields.items():
        body.extend(f"--{boundary}\r\n".encode())
        body.extend(f'Content-Disposition: form-data; name="{k}"\r\n\r\n'.encode())
        body.extend(f"{v}\r\n".encode())

    # face_crop
    body.extend(f"--{boundary}\r\n".encode())
    body.extend(f'Content-Disposition: form-data; name="face_crop"; filename="{crop_path.name}"\r\n'.encode())
    body.extend(b"Content-Type: image/jpeg\r\n\r\n")
    with open(crop_path, "rb") as f:
        body.extend(f.read())
    body.extend(b"\r\n")

    # full_frame
    body.extend(f"--{boundary}\r\n".encode())
    body.extend(f'Content-Disposition: form-data; name="full_frame"; filename="{frame_path.name}"\r\n'.encode())
    body.extend(b"Content-Type: image/jpeg\r\n\r\n")
    with open(frame_path, "rb") as f:
        body.extend(f.read())
    body.extend(b"\r\n")

    body.extend(f"--{boundary}--\r\n".encode())

    req = urllib.request.Request(
        f"{PROD_BASE_URL}/api/sightings/",
        data=bytes(body),
        headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "X-API-Key": AGENT_API_KEY,
        },
    )

    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            res = json.loads(r.read().decode())
            print(f"  [SIGHTING OK] Uploaded sighting ID={res.get('id')}, sim={res.get('similarity_score')} for {person_prod_id}")
            return True
    except Exception as e:
        print(f"  [SIGHTING ERR] Sighting failed: {e}")
        return False


def main():
    print("=" * 70)
    print("MIGRATING LOCALHOST MK SINHA DATA TO PRODUCTION (65.20.80.222)")
    print("=" * 70)

    db_path = ROOT_DIR / "backend_local.db"
    if not db_path.exists():
        print("backend_local.db not found!")
        return

    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    # 1. Upload missing photos
    print("\n[STEP 1] Checking missing photos for Asish...")
    upload_additional_photo("848355a4-56cc-4942-82bd-c0bb24abfc74", "uploads/photos/bdee4e15154a4a0e92afa99523375ed2.jpg")

    # 2. Upload sightings for all persons
    print("\n[STEP 2] Migrating Sightings from backend_local.db...")
    user_id = '8e764e846209461db3db0599a26bbb67'
    cur.execute("SELECT id, full_name FROM missing_persons WHERE user_id=?", (user_id,))
    local_persons = cur.fetchall()

    total_uploaded = 0
    for p in local_persons:
        local_id = p["id"]
        name = p["full_name"]
        prod_id = PROD_PERSON_MAP.get(local_id)
        if not prod_id:
            print(f"[WARN] No prod mapping for {name} ({local_id})")
            continue

        cur.execute("SELECT * FROM sightings WHERE person_id=?", (local_id,))
        sightings = [dict(s) for s in cur.fetchall()]
        print(f"\nProcessing {len(sightings)} sightings for {name} -> Prod ID {prod_id}...")

        for s in sightings:
            ok = upload_sighting(prod_id, s)
            if ok:
                total_uploaded += 1

    print(f"\n[DONE] Successfully migrated {total_uploaded} sightings to production!")

    # 3. Verify on production
    print("\n[STEP 3] Verifying production state...")
    for local_id, prod_id in PROD_PERSON_MAP.items():
        req = urllib.request.Request(
            f"{PROD_BASE_URL}/api/reports/{prod_id}/sightings",
            headers={"Authorization": f"Bearer {USER_TOKEN}"},
        )
        try:
            with urllib.request.urlopen(req, timeout=5) as r:
                s_list = json.loads(r.read().decode())
                print(f"  Prod Person {prod_id} now has {len(s_list)} sightings in production timeline.")
        except Exception as e:
            print(f"  Error checking sightings for {prod_id}: {e}")

    conn.close()


if __name__ == "__main__":
    main()
