import { useState } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { CalendarDays, ChevronLeft, ChevronRight } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { buildMonthGrid, cursorOf, monthLabel, shiftMonth, WEEKDAY_LABELS, type CalendarDay, type MonthCursor } from '@/domain/chat-calendar';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type ChatCalendarSheetProps = {
  onClose: () => void;
  onPickDay: (timestamp: number) => void;
  visible: boolean;
};

export function ChatCalendarSheet({ onClose, onPickDay, visible }: ChatCalendarSheetProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const insets = useSafeAreaInsets();
  const [cursor, setCursor] = useState<MonthCursor>(() => cursorOf(Date.now()));
  const cells = buildMonthGrid(cursor);

  const open = () => setCursor(cursorOf(Date.now()));
  const select = (day: CalendarDay) => {
    onClose();
    onPickDay(day.timestamp);
  };

  return (
    <Modal animationType="fade" onRequestClose={onClose} onShow={open} presentationStyle="overFullScreen" statusBarTranslucent transparent visible={visible}>
      <View style={styles.overlay}>
        <Pressable accessibilityLabel="Cerrar calendario" onPress={onClose} style={[StyleSheet.absoluteFill, { backgroundColor: theme.backdrop }]} />
        <View accessibilityViewIsModal style={[styles.sheet, { backgroundColor: theme.backgroundElement, borderColor: theme.border, paddingBottom: Math.max(insets.bottom, 12) }]}>
          <XStack alignItems="center" gap={10} paddingHorizontal={16} paddingVertical={14}>
            <CalendarDays color={theme.text} size={18} />
            <SizableText color={theme.text} flex={1} fontSize={scale(16)} fontWeight="700">Ir a una fecha</SizableText>
          </XStack>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <XStack alignItems="center" justifyContent="space-between" paddingHorizontal={12} paddingVertical={8}>
            <NativeRipplePressable accessibilityLabel="Mes anterior" onPress={() => setCursor((current) => shiftMonth(current, -1))} style={styles.arrow}>
              <ChevronLeft color={theme.text} size={20} />
            </NativeRipplePressable>
            <Text color={theme.text} fontSize={scale(14)} fontWeight="700" style={styles.month}>{monthLabel(cursor)}</Text>
            <NativeRipplePressable accessibilityLabel="Mes siguiente" onPress={() => setCursor((current) => shiftMonth(current, 1))} style={styles.arrow}>
              <ChevronRight color={theme.text} size={20} />
            </NativeRipplePressable>
          </XStack>
          <YStack paddingHorizontal={12} paddingBottom={8}>
            <XStack>
              {WEEKDAY_LABELS.map((label) => (
                <Text color={theme.placeholder} flex={1} fontSize={scale(11)} key={label} textAlign="center">{label}</Text>
              ))}
            </XStack>
            {chunkWeeks(cells).map((week, index) => (
              <XStack key={`week-${index}`}>
                {week.map((cell, position) => (
                  <View key={cell?.key ?? `empty-${index}-${position}`} style={styles.cell}>
                    {cell ? (
                      <NativeRipplePressable accessibilityLabel={`Ir al ${cell.day} de ${monthLabel(cursor)}`} onPress={() => select(cell)} style={styles.day}>
                        <Text color={theme.text} fontSize={scale(13)}>{cell.day}</Text>
                      </NativeRipplePressable>
                    ) : null}
                  </View>
                ))}
              </XStack>
            ))}
          </YStack>
        </View>
      </View>
    </Modal>
  );
}

function chunkWeeks(cells: readonly (CalendarDay | null)[]): (CalendarDay | null)[][] {
  const weeks: (CalendarDay | null)[][] = [];
  for (let index = 0; index < cells.length; index += 7) {
    weeks.push(cells.slice(index, index + 7));
  }
  return weeks;
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'flex-end', paddingHorizontal: 12 },
  sheet: { borderRadius: 18, borderWidth: StyleSheet.hairlineWidth, elevation: 14, overflow: 'hidden', shadowColor: '#000', shadowOffset: { height: 8, width: 0 }, shadowOpacity: 0.32, shadowRadius: 18 },
  divider: { height: StyleSheet.hairlineWidth, marginHorizontal: 16 },
  arrow: { alignItems: 'center', borderRadius: 20, height: 40, justifyContent: 'center', width: 40 },
  month: { textTransform: 'capitalize' },
  cell: { alignItems: 'center', flex: 1, paddingVertical: 2 },
  day: { alignItems: 'center', borderRadius: 18, height: 36, justifyContent: 'center', width: 36 },
});
