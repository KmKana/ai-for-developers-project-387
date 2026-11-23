import { NavLink, Route, Routes } from 'react-router-dom';

import BookingsPage from './pages/BookingsPage';
import BookingPage from './pages/BookingPage';
import BookingStubPage from './pages/BookingStubPage';
import CatalogPage from './pages/CatalogPage';
import EventTypesPage from './pages/EventTypesPage';
import HomePage from './pages/HomePage';

function navClass({ isActive }: { isActive: boolean }) {
  return isActive ? 'nav-link active' : 'nav-link';
}

export default function App() {
  return (
    <div className="app">
      <header className="topbar">
        <NavLink to="/" className="brand">
          <span className="brand-dot" />
          Запись на звонок
        </NavLink>
        <nav className="nav">
          <NavLink to="/catalog" end className={navClass}>
            Каталог
          </NavLink>
          <span className="nav-divider" aria-hidden="true" />
          <span className="nav-hint">владелец:</span>
          <NavLink to="/event-types" className={navClass}>
            Типы встреч
          </NavLink>
          <NavLink to="/bookings" className={navClass}>
            Встречи
          </NavLink>
        </nav>
      </header>

      <main className="content">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/catalog" element={<CatalogPage />} />
          <Route path="/booking" element={<BookingStubPage />} />
          <Route path="/book/:id" element={<BookingPage />} />
          <Route path="/event-types" element={<EventTypesPage />} />
          <Route path="/bookings" element={<BookingsPage />} />
        </Routes>
      </main>

      <footer className="footer">
        Демо-приложение по мотивам Cal.com. Данные хранятся в памяти процесса и сбрасываются при
        перезапуске.
      </footer>
    </div>
  );
}
