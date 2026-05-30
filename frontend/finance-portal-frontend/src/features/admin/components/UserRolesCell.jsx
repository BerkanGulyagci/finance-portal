export default function UserRolesCell({ roles }) {
  if (!roles?.length) {
    return <span className="text-gray-400 text-sm">—</span>;
  }
  return (
    <section className="flex flex-wrap gap-1">
      {roles.map(role => (
        <span
          key={role}
          className={`text-xs font-semibold px-2 py-0.5 rounded-md ${
            role === 'ADMIN'
              ? 'bg-violet-50 text-violet-700 border border-violet-200'
              : 'bg-gray-100 text-gray-600 border border-gray-200'
          }`}
        >
          {role}
        </span>
      ))}
    </section>
  );
}
