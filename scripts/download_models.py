import os
from insightface.app import FaceAnalysis

def main():
    model_dir = os.environ.get('AI_MODEL_DIR', './ai_models')
    os.makedirs(model_dir, exist_ok=True)
    
    print(f"Downloading InsightFace models to {model_dir}...")
    app = FaceAnalysis(name='buffalo_l', root=model_dir, providers=['CPUExecutionProvider'])
    app.prepare(ctx_id=-1, det_size=(640, 640))
    print("Models downloaded successfully.")

if __name__ == "__main__":
    main()
