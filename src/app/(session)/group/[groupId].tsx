import { router, useLocalSearchParams } from 'expo-router';

import { GroupInfoScreen } from '@/components/chat/group-info-screen';

export default function GroupInfoRoute() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  return <GroupInfoScreen
    groupId={groupId ?? ''}
    onBack={() => router.back()}
    onOpenMessage={(messageId) => router.push({ pathname: '/chat/[upeerId]', params: { upeerId: groupId ?? '', messageId } })}
  />;
}
