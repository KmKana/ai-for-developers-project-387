import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';

import { createBooking, fetchMeetingTypes, fetchSlots, isConflict } from '../api';
import type { Booking, MeetingType, Slot } from '../sdk';
import {
  bookingWindowDays,
  dayKey,
  dayParts,
  formatDateTime,
  formatDuration,
  formatTime,
  isWeekend,
  slotDurationMinutes,
} from '../util/format';

/** Страница записи: календарь на 14 дней, слоты и форма гостя. */
export default function BookingPage() {
  const { id } = useParams<{ id: string }>();

  const [type, setType] = useState<MeetingType | null>(null);
  const [slots, setSlots] = useState<Slot[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [selectedDay, setSelectedDay] = useState<string | null>(null);
  const [selectedSlot, setSelectedSlot] = useState<Slot | null>(null);

  const [guestName, setGuestName] = useState('');
  const [guestEmail, setGuestEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [conflict, setConflict] = useState<string | null>(null);
  const [booked, setBooked] = useState<Booking | null>(null);

  const load = useCallback(async () => {
    if (!id) {
      return;
    }
    try {
      const [allTypes, slotList] = await Promise.all([fetchMeetingTypes(), fetchSlots(id)]);
      const found = allTypes.find((t) => t.id === id) ?? null;
      setType(found);
      setSlots(slotList);
      setLoaded(true);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Ошибка загрузки');
      setLoaded(true);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  const byDay = useMemo(() => {
    const map = new Map<string, Slot[]>();
    for (const slot of slots) {
      const key = dayKey(slot.start);
      const list = map.get(key) ?? [];
      list.push(slot);
      map.set(key, list);
    }
    return map;
  }, [slots]);

  // Календарь не зависит от ответа SDK: показываем все 14 дней окна, включая
  // выходные и полностью занятые дни, даже если сервер не вернул для них Слоты.
  const days = useMemo(() => bookingWindowDays(), []);

  useEffect(() => {
    if (days.length > 0 && (selectedDay === null || !days.includes(selectedDay))) {
      setSelectedDay(days.find((day) => !isWeekend(day)) ?? days[0]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [days]);

  const daySlots = selectedDay ? (byDay.get(selectedDay) ?? []) : [];

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!id || !selectedSlot) {
      return;
    }
    setSubmitting(true);
    setConflict(null);
    try {
      const booking = await createBooking({
        meetingTypeId: id,
        start: selectedSlot.start,
        guestName,
        guestEmail,
      });
      setBooked(booking);
    } catch (e) {
      if (isConflict(e)) {
        setConflict(e instanceof Error ? e.message : 'Это время уже недоступно');
        setSelectedSlot(null);
        await load();
      } else {
        setConflict(e instanceof Error ? e.message : 'Не удалось записаться');
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (booked) {
    return (
      <div className="page">
        <div className="panel success" data-testid="booking-success">
          <h2>Встреча забронирована!</h2>
          <p className="muted">Мы держим за вами это время. Детали ниже.</p>
          <dl className="details">
            <div>
              <dt>Встреча</dt>
              <dd>{booked.meetingTypeName}</dd>
            </div>
            <div>
              <dt>Когда</dt>
              <dd>
                {formatDateTime(booked.start)} – {formatTime(booked.end)} (Asia/Almaty)
              </dd>
            </div>
            <div>
              <dt>Гость</dt>
              <dd>
                {booked.guestName} · {booked.guestEmail}
              </dd>
            </div>
          </dl>
          <Link className="btn btn-secondary" to="/catalog">
            Записаться ещё раз
          </Link>
        </div>
      </div>
    );
  }

  if (!loaded) {
    return (
      <div className="page">
        <div className="muted">Загрузка…</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page">
        <div className="banner banner-error">{error}</div>
        <Link className="btn btn-secondary" to="/">
          В каталог
        </Link>
      </div>
    );
  }

  if (!type) {
    return (
      <div className="page">
        <div className="empty">
          Такой тип встречи не найден. Возможно, данные сброшены после перезапуска сервиса.
        </div>
        <Link className="btn btn-secondary" to="/">
          В каталог
        </Link>
      </div>
    );
  }

  return (
    <div className="page booking-layout">
      <aside className="booking-summary">
        <Link to="/" className="back-link">
          ← Kanybek
        </Link>
        <h2>{type.name}</h2>
        <p className="chip">{formatDuration(type.durationMinutes)}</p>
        {type.description && <p className="muted">{type.description}</p>}
        <p className="muted small">Часовой пояс: Asia/Almaty · пн–пт, 09:00–18:00</p>
      </aside>

      <section className="booking-main">
        <h3 className="step-title">1. Выберите день</h3>
        <div className="day-strip">
          {days.map((day) => {
            const parts = dayParts(`${day}T09:00:00+05:00`);
            const active = day === selectedDay;
            const weekend = isWeekend(day);
            return (
              <button
                key={day}
                type="button"
                data-testid="day-chip"
                data-day={day}
                disabled={weekend}
                className={`day-chip${active ? ' active' : ''}${weekend ? ' weekend' : ''}`}
                onClick={() => {
                  setSelectedDay(day);
                  setSelectedSlot(null);
                }}
              >
                <span className="weekday">{parts.weekday}</span>
                <span className="day">{parts.day}</span>
                <span className="month">{parts.month}</span>
              </button>
            );
          })}
        </div>

        <h3 className="step-title">2. Выберите время</h3>
        {daySlots.length === 0 ? (
          <div className="empty">В этот день свободных слотов нет — выберите другой день.</div>
        ) : (
          <div className="slot-grid">
            {daySlots.map((slot) => {
              const active = selectedSlot?.start === slot.start;
              return (
                <button
                  key={slot.start}
                  type="button"
                  data-testid="slot-button"
                  data-start={slot.start}
                  className={`slot${active ? ' active' : ''}`}
                  onClick={() => setSelectedSlot(slot)}
                >
                  {formatTime(slot.start)} · {formatDuration(slotDurationMinutes(slot))}
                </button>
              );
            })}
          </div>
        )}

        <h3 className="step-title">3. Представьтесь</h3>
        {conflict && (
          <div className="banner banner-error" data-testid="booking-conflict">
            {conflict}
          </div>
        )}
        <form className="panel form" onSubmit={(e) => void submit(e)}>
          <label>
            Имя
            <input
              data-testid="guest-name"
              value={guestName}
              onChange={(e) => setGuestName(e.target.value)}
              placeholder="Как вас зовут?"
              required
            />
          </label>
          <label>
            Email
            <input
              data-testid="guest-email"
              value={guestEmail}
              onChange={(e) => setGuestEmail(e.target.value)}
              type="email"
              placeholder="you@example.com"
              required
            />
          </label>
          <button
            className="btn"
            type="submit"
            data-testid="submit-booking"
            disabled={!selectedSlot || submitting}
          >
            {selectedSlot
              ? `Подтвердить запись на ${formatDateTime(selectedSlot.start)}`
              : 'Сначала выберите время'}
          </button>
        </form>
      </section>
    </div>
  );
}
