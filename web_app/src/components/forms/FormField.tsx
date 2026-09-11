import React from 'react';

interface FormFieldProps {
  label: string;
  id?: string;
  error?: string | null;
  children: React.ReactNode;
  required?: boolean;
}

export const FormField: React.FC<FormFieldProps> = ({ label, id, error, children, required }) => {
  const errorId = error && id ? `${id}-error` : undefined;
  const showRequiredMarker = required && !label.trim().endsWith('*');

  return (
    <div className="form-group">
      <label className="form-label" htmlFor={id}>
        {label} {showRequiredMarker && <span className="required" aria-hidden="true">*</span>}
      </label>
      {children}
      {error && <span className="form-error" id={errorId} role="alert">{error}</span>}
    </div>
  );
};
