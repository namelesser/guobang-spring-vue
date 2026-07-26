import { h } from 'vue';
import SvgIcon from '@/components/custom/svg-icon.vue';

export function useSvgIcon() {
  return {
    SvgIconVNode: (props: Record<string, unknown>) => () => h(SvgIcon, props)
  };
}
