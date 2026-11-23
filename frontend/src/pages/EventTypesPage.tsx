import { useEffect, useState, type FormEvent } from 'react';

import { createMeetingType, fetchMeetingTypes } from '../api';
import type { MeetingType } from '../sdk';
import { formatDuration } from '../util/format';

const DURATION_PRESETS = [15, 30, 45, 60];

/** Страница владельца: создание типов встреч и каталог. */
export default function EventTypesPage() {
  const [types, setTypes] = useState<MeetingType[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [duration, setDuration] = useState<number>(30);
  const [creating, setCreating] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    fetchMeetingTypes()
      .then((list) => {
        setTypes(list);
        setLoaded(true);
      })
      .catch((e: Error) => {
        setError(e.message);
        setLoaded(true);
      });
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setCreating(true);
    setFormError(null);
    try {
      const created = await createMeetingType({
        name,
        description: description || undefined,
        durationMinutes: duration,
      });
      setTypes((prev) => [...prev, created]);
      setName('');
      setDescription('');
      setDuration(30);
    } catch (e) {
      setFormError(e instanceof Error ? e.message : 'Не удалось создать тип встречи');
    } finally {
      setCreating(false);
    }
  }

  return (
    <div className="page">
      <h1>Типы встреч</h1>
      <p className="muted">
        Создайте тип встречи: гости увидят его в каталоге и смогут записаться.
      </p>

      {error && <div className="banner banner-error">{error}</div>}

      <div className="owner-layout">
        <form className="panel form" onSubmit={(e) => void submit(e)}>
          <h3>Новый тип встречи</h3>
          <label>
            Название
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Знакомство"
              required
            />
          </label>
          <label>
            Описание
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Короткий созвон: знакомимся и обсуждаем задачу"
              rows={3}
            />
          </label>
          <label>
            Длительность, минуты
            <div className="preset-row">
              {DURATION_PRESETS.map((preset) => (
                <button
                  key={preset}
                  type="button"
                  className={`preset${duration === preset ? ' active' : ''}`}
                  onClick={() => setDuration(preset)}
                >
                  {preset}
                </button>
              ))}
              <input
                type="number"
                min={5}
                max={480}
                value={duration}
                onChange={(e) => setDuration(Number(e.target.value))}
              />
            </div>
          </label>
          {formError && <div className="banner banner-error">{formError}</div>}
          <button className="btn" type="submit" disabled={creating}>
            {creating ? 'Создаём…' : 'Создать тип встречи'}
          </button>
        </form>

        <section>
          <h3 className="step-title">Каталог ({types.length})</h3>
          {loaded && !error && types.length === 0 && (
            <div className="empty">Типов встреч пока нет.</div>
          )}
          <div className="type-list">
            {types.map((type) => (
              <article key={type.id} className="type-row">
                <div>
                  <strong>{type.name}</strong>
                  <span className="chip">{formatDuration(type.durationMinutes)}</span>
                </div>
                {type.description && <p className="muted small">{type.description}</p>}
              </article>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}
