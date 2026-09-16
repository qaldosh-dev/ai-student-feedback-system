import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Route, Routes } from 'react-router';
import BrowsePage from './pages/BrowsePage';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<BrowsePage />} />
      </Routes>
    </BrowserRouter>
  </React.StrictMode>,
);
