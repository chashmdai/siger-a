import {useCallback, useState} from 'react';

export const reducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;

/** Cierre con animacion de salida breve; inmediato si la persona prefiere movimiento reducido. */
export function useExit(close: () => void, ms = 150) {
  const [leaving, setLeaving] = useState(false);
  const exit = useCallback(() => {
    if (reducedMotion()) {close(); return;}
    setLeaving(true);
    window.setTimeout(close, ms);
  }, [close, ms]);
  return [leaving, exit] as const;
}
