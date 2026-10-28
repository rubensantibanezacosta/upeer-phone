import { useEffect, useSyncExternalStore } from 'react';

import { scoreOf, type ReputationScores } from '@/domain/reputation';
import {
  getReputationScoresSnapshot,
  setReputationSelf,
  startReputationPolling,
  subscribeReputationScores,
} from '@/storage/reputation-store';

export function useReputationScores(): ReputationScores {
  useEffect(() => {
    startReputationPolling();
  }, []);
  return useSyncExternalStore(subscribeReputationScores, getReputationScoresSnapshot, getReputationScoresSnapshot);
}

export function useVouchScore(upeerId?: string | null): number | undefined {
  return scoreOf(useReputationScores(), upeerId);
}

export function useOwnVouchScore(upeerId?: string | null): number | undefined {
  useEffect(() => {
    if (upeerId) setReputationSelf(upeerId);
  }, [upeerId]);
  return useVouchScore(upeerId);
}
