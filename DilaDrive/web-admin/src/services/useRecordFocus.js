import { useEffect } from "react";

export default function useRecordFocus({ id, items, idKey, loading, error, open, complete }) {
  useEffect(() => {
    if (id == null || loading) return;
    if (error) {
      complete?.("The linked record could not be loaded. Check your access or refresh the page.");
      return;
    }
    const item = items.find(item => String(item[idKey]) === String(id));
    if (!item) {
      complete?.("The linked record is no longer available or you do not have permission to view it.");
      return;
    }
    complete?.();
    open(item);
  }, [id, items, idKey, loading, error, open, complete]);
}
