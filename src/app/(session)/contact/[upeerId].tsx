import { router, useLocalSearchParams } from 'expo-router';

import { ContactInfoScreen } from '@/components/contact-info/contact-info-screen';

export default function ContactInfoRoute() {
  const { upeerId } = useLocalSearchParams<{ upeerId: string }>();
  return <ContactInfoScreen onBack={() => router.back()} upeerId={upeerId ?? ''} />;
}
