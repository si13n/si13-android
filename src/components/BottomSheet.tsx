import { useState } from "react";
import "../styles/fonts.css";
import {
  Calendar,
  ChevronRight,
  Clock,
  Flag,
  Repeat2,
  Trash2,
} from "lucide-react";

type Task = {
  id: number;
  title: string;
};

type BottomSheetProps = {
  task: Task;
  onClose: () => void;
};

export default function BottomSheet({ task, onClose }: BottomSheetProps) {
  const [completed, setCompleted] = useState(false);

  return (
    <div className="absolute inset-0 z-50 flex flex-col justify-end font-['Roboto',sans-serif]">
      <button
        aria-label="Close task details"
        className="absolute inset-0 z-0 w-full bg-black/30"
        onClick={onClose}
        type="button"
      />

      <section
        aria-label="Task details"
        className="relative z-10 flex max-h-[78%] w-full flex-col overflow-hidden rounded-t-3xl bg-white shadow-[0_-4px_32px_rgba(0,0,0,0.14)]"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="pb-1 pt-3">
          <div className="mx-auto h-1 w-8 rounded-full bg-gray-300" />
        </div>

        <div className="flex items-center justify-between px-4 pb-3 pt-2">
          <h2 className="text-lg font-medium text-gray-900">{task.title}</h2>
          <span className="text-xs font-normal text-gray-400">3 / 100</span>
        </div>

        <div className="border-t border-gray-100" />

        <div className="min-h-0 flex-1 overflow-y-auto">
          <div>
            <DetailRow icon={Flag} label="Priority" value="None" />
            <div className="mx-4 border-t border-gray-100" />
            <DetailRow icon={Calendar} label="Due date" value="No due date" />
            <div className="mx-4 border-t border-gray-100" />
            <DetailRow icon={Repeat2} label="Repeat" value="Does not repeat" />
          </div>

          <div className="flex flex-col px-4 pb-1 pt-3">
            <label
              className="mb-2 text-[11px] font-medium uppercase tracking-[0.08em] text-gray-400"
              htmlFor={`task-note-${task.id}`}
            >
              Note
            </label>
            <textarea
              className="min-h-12 w-full resize-none border-0 bg-transparent p-0 text-sm leading-5 text-gray-700 outline-none placeholder:text-gray-400"
              id={`task-note-${task.id}`}
              placeholder="Add a note..."
              rows={2}
            />
          </div>

          <div className="flex items-center gap-1.5 px-4 pb-4 pt-1 text-[11px] text-gray-400">
            <Clock className="shrink-0" size={13} />
            <span>Created Jul 28, 2026 at 12:51 PM</span>
          </div>

          <div className="border-t border-gray-100" />

          <div className="flex flex-col gap-3 px-4 pb-6 pt-4">
            <button
              className={`h-[52px] w-full rounded-full text-sm font-medium text-white transition-colors duration-200 ${
                completed ? "bg-[#4CAF50]" : "bg-[#6750A4]"
              }`}
              onClick={() => setCompleted((current) => !current)}
              type="button"
            >
              {completed ? "Completed!" : "Mark complete"}
            </button>

            <button
              className="flex self-center items-center gap-2 px-4 py-2 text-sm font-medium text-red-500"
              type="button"
            >
              <Trash2 size={16} />
              Delete task
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}

type DetailRowProps = {
  icon: typeof Flag;
  label: string;
  value: string;
};

function DetailRow({ icon: Icon, label, value }: DetailRowProps) {
  return (
    <button
      className="flex w-full cursor-pointer items-center gap-4 px-4 py-3.5 text-left active:bg-gray-50"
      type="button"
    >
      <Icon className="shrink-0 text-gray-500" size={18} />
      <span className="flex-1 text-sm text-gray-900">{label}</span>
      <span className="shrink-0 whitespace-nowrap text-sm text-gray-500">{value}</span>
      <ChevronRight className="shrink-0 text-gray-400" size={16} />
    </button>
  );
}
