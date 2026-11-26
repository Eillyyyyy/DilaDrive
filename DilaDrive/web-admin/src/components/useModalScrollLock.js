import { useEffect, useLayoutEffect } from "react";

const useBrowserLayoutEffect =
  typeof document === "undefined" ? useEffect : useLayoutEffect;
let openModalCount = 0;

export default function useModalScrollLock() {
  useBrowserLayoutEffect(() => {
    const root = document.documentElement;
    openModalCount += 1;
    root.classList.add("modal-scroll-locked");

    return () => {
      openModalCount -= 1;
      if (openModalCount === 0) root.classList.remove("modal-scroll-locked");
    };
  }, []);
}
