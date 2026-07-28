import { useState } from "react";
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
    <div className="absolute inset-0 z-10 font-['Roboto']">
      <button
        aria-label="Close task details"
        className="absolute inset-0 w-full bg-black/30"
        onClick={onClose}
        type="button"
      />

      <section
        aria-label="Task details"
        className="absolute bottom-0 flex max-h-[78%] w-full flex-col overflow-y-auto rounded-t-3xl bg-white shadow-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="pb-1 pt-3">
          <div className="mx-auto h-1 w-8 rounded-full bg-gray-300" />
        </div>

        <div className="flex items-center justify-between px-4 pb-3 pt-2">
          <h2 className="text-lg font-medium text-gray-900">{task.title}</h2>
          <span className="text-xs text-gray-400">{task.title.length} / 100</span>
        </div>

        <div className="border-t border-gray-100" />

        <div>
          <DetailRow icon={Flag} label="Priority" value="None" />
          <div className="mx-4 border-t border-gray-100" />
          <DetailRow icon={Calendar} label="Due date" value="No due date" />
          <div className="mx-4 border-t border-gray-100" />
          <DetailRow icon={Repeat2} label="Repeat" value="Does not repeat" />
        </div>

        <div className="px-4 pb-1 pt-3">
          <label
            className="mb-2 block text-xs font-medium uppercase tracking-wide text-gray-400"
            htmlFor={`task-note-${task.id}`}
          >
            Note
          </label>
          <textarea
            className="w-full resize-none border-0 bg-transparent p-0 text-sm text-gray-500 outline-none placeholder:text-gray-500"
            id={`task-note-${task.id}`}
            placeholder="Add a note..."
            rows={2}
          />
        </div>

        <div className="flex items-center gap-1.5 px-4 pb-4 text-xs text-gray-400">
          <Clock size={13} />
          <span>Created Jul 28, 2026 at 12:51 PM</span>
        </div>

        <div className="border-t border-gray-100" />

        <div className="flex flex-col gap-3 px-4 pb-6 pt-4">
          <button
            className={`w-full rounded-full py-3.5 text-sm font-medium text-white ${
              completed ? "bg-green-500" : "bg-[#6750A4]"
            }`}
            onClick={() => setCompleted((current) => !current)}
            type="button"
          >
            {completed ? "Completed!" : "Mark complete"}
          </button>

          <button
            className="flex items-center justify-center gap-2 text-sm font-medium text-red-500"
            type="button"
          >
            <Trash2 size={16} />
            Delete task
          </button>
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
      className="flex w-full items-center gap-4 px-4 py-3.5 text-left"
      type="button"
    >
      <Icon className="text-gray-500" size={18} />
      <span className="flex-1 text-sm text-gray-900">{label}</span>
      <span className="text-sm text-gray-500">{value}</span>
      <ChevronRight className="text-gray-400" size={16} />
    </button>
  );
}
