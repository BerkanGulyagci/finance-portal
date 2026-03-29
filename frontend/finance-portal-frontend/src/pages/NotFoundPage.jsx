import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div>
      <h1>404 — Sayfa Bulunamadı</h1>
      <p>Aradığınız sayfa mevcut değil.</p>
      <Link to="/dashboard">Dashboard'a dön</Link>
    </div>
  );
}
