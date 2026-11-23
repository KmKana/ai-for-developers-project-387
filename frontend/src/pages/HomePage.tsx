import { Link } from 'react-router-dom';

/** Главная страница: рассказывает о сервисе и ведёт к записи. */
export default function HomePage() {
  return (
    <div className="page">
      <section className="hero">
        <div>
          <h1>Запись на звонок</h1>
          <p className="muted">
            Упрощённый сервис бронирования времени по мотивам Cal.com: владелец публикует типы
            встреч и доступное время, а гость выбирает свободный слот и записывается на звонок.
          </p>
          <Link className="btn home-cta" to="/booking">
            Записаться на встречу
          </Link>
        </div>
      </section>
    </div>
  );
}
