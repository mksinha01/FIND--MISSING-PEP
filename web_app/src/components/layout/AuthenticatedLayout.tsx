import React from 'react';
import { Outlet } from 'react-router-dom';
import { Navbar } from './Navbar';
import { Footer } from './Footer';

export const AuthenticatedLayout: React.FC = () => {
  return (
    <div className="app-shell">
      <Navbar />
      <div className="app-surface">
        <main className="app-main">
          <Outlet />
        </main>
        <Footer />
      </div>
    </div>
  );
};
