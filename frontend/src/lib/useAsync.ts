import { useEffect, useState, type DependencyList } from "react";
import { errorMessage } from "../api/client";

export interface AsyncState<T> {
  data: T | null;
  error: string | null;
  loading: boolean;
}

/**
 * Runs `fn` whenever `deps` change and ignores results from superseded runs,
 * so quickly switching companies can never show one company's data under
 * another's header. `reloadKey` lets a caller force a re-run.
 */
export function useAsync<T>(fn: () => Promise<T>, deps: DependencyList): AsyncState<T> {
  const [state, setState] = useState<AsyncState<T>>({ data: null, error: null, loading: true });

  useEffect(() => {
    let current = true;
    setState({ data: null, error: null, loading: true });
    fn().then(
      (data) => current && setState({ data, error: null, loading: false }),
      (e) => current && setState({ data: null, error: errorMessage(e), loading: false }),
    );
    return () => {
      current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return state;
}
