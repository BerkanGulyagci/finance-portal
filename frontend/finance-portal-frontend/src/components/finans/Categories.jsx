import { Link } from 'react-router-dom';

const categories = [
  { name: 'Genel Ekonomi', path: '/news', image: 'https://images.unsplash.com/photo-1526304640581-d334cdbbf45e?w=400&h=200&fit=crop' },
  { name: 'Hisse Senedi', path: '/market', image: 'https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=400&h=200&fit=crop' },
  { name: 'Döviz', path: '/market', image: 'https://images.unsplash.com/photo-1621761191319-c6fb62004040?w=400&h=200&fit=crop' },
  { name: 'Tahvil & Bono', path: '/market', image: 'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=400&h=200&fit=crop' },
  { name: 'Portföy', path: '/portfolio', image: 'https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=400&h=200&fit=crop' },
  { name: 'Kripto', path: '/market', image: 'https://images.unsplash.com/photo-1518546305927-5a555bb7020d?w=400&h=200&fit=crop' },
];

export function Categories() {
  return (
    <section className="mb-12">
      <h2 className="text-xl font-bold mb-6 flex items-center justify-between">
        Kategorilere Göz Atın
        <Link to="/market" className="text-sm font-semibold text-[#093eaa] hover:underline">
          Tümünü Gör
        </Link>
      </h2>
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        {categories.map((cat) => (
          <Link key={cat.name} to={cat.path}
            className="relative h-24 rounded-xl overflow-hidden group cursor-pointer shadow-sm block">
            <div className="absolute inset-0 bg-black/40 group-hover:bg-[#093eaa]/60 transition-colors z-10 flex items-center justify-center">
              <span className="text-white font-bold text-sm text-center px-2">{cat.name}</span>
            </div>
            <div className="absolute inset-0 bg-cover bg-center group-hover:scale-110 transition-transform duration-300"
              style={{ backgroundImage: `url('${cat.image}')` }} />
          </Link>
        ))}
      </div>
    </section>
  );
}
