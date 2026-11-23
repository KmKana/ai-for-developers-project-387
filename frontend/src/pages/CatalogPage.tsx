import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { fetchMeetingTypes } from '../api';
import type { MeetingType } from '../sdk';
import { formatDuration } from '../util/format';

/** Главная страница гостя: каталог типов встреч владельца. */
export default function CatalogPage() {
  const [types, setTypes] = useState<MeetingType[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchMeetingTypes()
      .then(setTypes)
      .catch((e: Error) => setError(e.message));
  }, []);

  return (
    <div className="page">
      <section className="hero">
        <div className="avatar">K</div>
        <div>
          <h1>Kanybek</h1>
          <p className="muted">Запишитесь на бесплатный звонок — выберите подходящий формат.</p>
        </div>
      </section>

      {error && <div className="banner banner-error">{error}</div>}

      {types === null && !error && <div className="muted">Загрузка…</div>}

      {types !== null && types.length === 0 && (
        <div className="empty">Владелец ещё не создал типы встреч. Загляните позже.</div>
      )}

      <div className="card-grid">
        {types?.map((type) => (
          <article key={type.id} className="type-card" data-testid="meeting-type-card">
            <div className="type-card-head">
              <h3>{type.name}</h3>
              <span className="chip">{formatDuration(type.durationMinutes)}</span>
            </div>
            {type.description && <p className="muted">{type.description}</p>}
            <Link className="btn" to={`/book/${type.id}`} data-testid="book-link">
              Записаться
            </Link>
          </article>
        ))}
      </div>
    </div>
  );
}
