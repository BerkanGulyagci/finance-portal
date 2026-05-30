export default function GoldLoadingState() {
  return (
    <div className="flex items-center gap-2 py-12 justify-center">
      <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
      <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
      <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
    </div>
  );
}
