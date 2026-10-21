import { useEffect, useMemo, useState } from 'react';
import { useLinkingURL } from 'expo-linking';
import { router, useGlobalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { SizableText, Text } from 'tamagui';

import { registerPeer, sendChat } from '../../../modules/upeer-transport/src/UPeerTransportModule';
import { useTheme } from '@/hooks/use-theme';

type DevSendParams = { upeerId?: string; address?: string; text?: string };

function parseQuery(url: string | null): DevSendParams {
  if (!url) return {};
  try {
    const parsed = new URL(url);
    return {
      upeerId: parsed.searchParams.get('upeerId') ?? undefined,
      address: parsed.searchParams.get('address') ?? undefined,
      text: parsed.searchParams.get('text') ?? undefined,
    };
  } catch {
    return {};
  }
}

export default function DevSendRoute() {
  const theme = useTheme();
  const params = useGlobalSearchParams<DevSendParams>();
  const url = useLinkingURL();
  const query = useMemo(() => parseQuery(url), [url]);
  const upeerId = params.upeerId || query.upeerId;
  const address = params.address || query.address;
  const text = params.text || query.text;
  const [result, setResult] = useState(`Esperando… ${JSON.stringify({ upeerId, address, text })}`);

  useEffect(() => {
    const timer = setTimeout(() => {
      void (async () => {
        if (!upeerId || !address) {
          setResult('Faltan upeerId o address');
          return;
        }
        try {
          const registered = await registerPeer(upeerId, address, '', '');
          const messageID = await sendChat(upeerId, text || 'Hola desde el móvil', '');
          setResult(`registered=${registered} message=${messageID}`);
          setTimeout(() => router.back(), 2500);
        } catch (cause) {
          setResult(cause instanceof Error ? cause.message : String(cause));
        }
      })();
    }, 0);
    return () => clearTimeout(timer);
  }, [address, text, upeerId]);

  return <SafeAreaView style={{ backgroundColor: theme.background, flex: 1, padding: 20 }}><SizableText color={theme.text} fontSize={18} fontWeight="700">Dev send</SizableText><Text color={theme.placeholder}>{result}</Text></SafeAreaView>;
}
