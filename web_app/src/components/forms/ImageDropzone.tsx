import React, { useRef, useState } from 'react';
import { Star, UploadCloud, X } from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';
import { compressImage, CompressedImage } from '../../services/imageService';

interface ImageDropzoneProps {
  maxPhotos?: number;
  photos: CompressedImage[];
  onPhotosChange: (photos: CompressedImage[]) => void;
  error?: string | null;
}

export const ImageDropzone: React.FC<ImageDropzoneProps> = ({ maxPhotos = 5, photos, onPhotosChange, error }) => {
  const { t } = useLanguage();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [isCompressing, setIsCompressing] = useState(false);

  const processFiles = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setIsCompressing(true);

    const availableSlots = maxPhotos - photos.length;
    const filesToProcess = Array.from(files).slice(0, availableSlots);
    const newCompressed: CompressedImage[] = [];

    for (const file of filesToProcess) {
      if (!file.type.startsWith('image/')) continue;
      try {
        newCompressed.push(await compressImage(file));
      } catch (compressionError) {
        console.error('Failed to compress image', file.name, compressionError);
      }
    }

    onPhotosChange([...photos, ...newCompressed]);
    setIsCompressing(false);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleDrop = (event: React.DragEvent) => {
    event.preventDefault();
    setIsDragging(false);
    void processFiles(event.dataTransfer.files);
  };

  const handleRemove = (index: number) => {
    const updated = [...photos];
    URL.revokeObjectURL(updated[index].previewUrl);
    updated.splice(index, 1);
    onPhotosChange(updated);
  };

  const handleSetPrimary = (index: number) => {
    if (index === 0) return;
    const updated = [...photos];
    const [selected] = updated.splice(index, 1);
    updated.unshift(selected);
    onPhotosChange(updated);
  };

  return (
    <div className="form-group upload-field">
      <label className="form-label">{t.form.photosTitle}</label>

      {photos.length < maxPhotos && (
        <div
          className={`dropzone${isDragging ? ' is-dragging' : ''}`}
          onDragOver={(event) => { event.preventDefault(); setIsDragging(true); }}
          onDragLeave={() => setIsDragging(false)}
          onDrop={handleDrop}
          onClick={() => fileInputRef.current?.click()}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              fileInputRef.current?.click();
            }
          }}
          role="button"
          tabIndex={0}
          aria-label="Upload clear face photos"
        >
          <input
            ref={fileInputRef}
            type="file"
            multiple
            accept="image/*"
            className="dropzone__input"
            onChange={(event) => void processFiles(event.target.files)}
          />
          <span className="dropzone__icon"><UploadCloud size={25} aria-hidden="true" /></span>
          <span className="dropzone__title">{isCompressing ? 'Compressing photos...' : t.form.clickOrDrop}</span>
          <span className="dropzone__hint">{t.form.photosSub} ({photos.length}/{maxPhotos})</span>
        </div>
      )}

      {photos.length > 0 && (
        <div className="preview-grid">
          {photos.map((item, index) => (
            <div key={`${item.previewUrl}-${index}`} className={`preview-item${index === 0 ? ' is-primary' : ''}`}>
              <img src={item.previewUrl} alt={`Upload preview ${index + 1}`} />
              {index === 0 ? (
                <span className="preview-item__primary"><Star size={10} fill="currentColor" aria-hidden="true" /> {t.form.primaryPhotoBadge}</span>
              ) : (
                <button type="button" className="preview-item__set-primary" onClick={(event) => { event.stopPropagation(); handleSetPrimary(index); }}>
                  <Star size={11} aria-hidden="true" /> Set primary
                </button>
              )}
              <button
                type="button"
                className="preview-item__remove"
                onClick={(event) => { event.stopPropagation(); handleRemove(index); }}
                aria-label={`${t.form.removePhoto} ${index + 1}`}
              >
                <X size={14} aria-hidden="true" />
              </button>
              <span className="preview-item__size">{Math.round(item.compressedSize / 1024)} KB</span>
            </div>
          ))}
        </div>
      )}

      {error && <span className="form-error" role="alert">{error}</span>}
    </div>
  );
};
