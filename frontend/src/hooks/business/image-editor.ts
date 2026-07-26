import { nextTick, ref } from 'vue';
import { useMessage } from 'naive-ui';

export interface CropRect {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface UseImageEditorOptions {
  onSave: (base64: string) => Promise<void>;
  getInitialBase64?: () => string;
}

export function useImageEditor(options: UseImageEditorOptions) {
  const message = useMessage();
  const editorOpen = ref(false);
  const canvasEl = ref<HTMLCanvasElement | null>(null);

  let editorBase = '';
  let editorImage: HTMLImageElement | null = null;
  let crop: CropRect | null = null;
  let dragging = false;

  async function openEditor(base64: string) {
    editorBase = base64;
    editorImage = null;
    crop = null;
    editorOpen.value = true;
    await nextTick();
    await loadEditorImage();
  }

  function loadEditorImage(): Promise<void> {
    return new Promise(resolve => {
      if (!editorBase) return resolve();
      const img = new Image();
      img.addEventListener(
        'load',
        () => {
          editorImage = img;
          const canvas = canvasEl.value;
          if (canvas) {
            canvas.width = img.naturalWidth;
            canvas.height = img.naturalHeight;
          }
          redraw();
          resolve();
        },
        { once: true }
      );
      img.src = editorBase;
    });
  }

  function redraw() {
    const canvas = canvasEl.value;
    const img = editorImage;
    if (!canvas || !img) return;
    if (canvas.width !== img.naturalWidth || canvas.height !== img.naturalHeight) {
      canvas.width = img.naturalWidth;
      canvas.height = img.naturalHeight;
    }
    const ctx = canvas.getContext('2d')!;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(img, 0, 0);
    if (crop) {
      const x = crop.w < 0 ? crop.x + crop.w : crop.x;
      const y = crop.h < 0 ? crop.y + crop.h : crop.y;
      const w = Math.abs(crop.w);
      const h = Math.abs(crop.h);
      ctx.save();
      ctx.strokeStyle = '#22c55e';
      ctx.lineWidth = Math.max(4, canvas.width / 500);
      ctx.setLineDash([16, 10]);
      ctx.strokeRect(x, y, w, h);
      ctx.fillStyle = 'rgba(34,197,94,.16)';
      ctx.fillRect(x, y, w, h);
      ctx.restore();
    }
  }

  async function updateEditorBase(base64: string) {
    editorBase = base64;
    editorImage = null;
    crop = null;
    await loadEditorImage();
  }

  function canvasToBase64(canvas: HTMLCanvasElement) {
    return canvas.toDataURL('image/jpeg', 0.95);
  }

  function point(e: MouseEvent) {
    const canvas = canvasEl.value!;
    const rect = canvas.getBoundingClientRect();
    return {
      x: Math.max(0, Math.min(canvas.width, ((e.clientX - rect.left) * canvas.width) / rect.width)),
      y: Math.max(0, Math.min(canvas.height, ((e.clientY - rect.top) * canvas.height) / rect.height))
    };
  }

  function startDrag(e: MouseEvent) {
    const p = point(e);
    dragging = true;
    crop = { x: p.x, y: p.y, w: 0, h: 0 };
    redraw();
  }

  function onDrag(e: MouseEvent) {
    if (!dragging || !crop) return;
    const p = point(e);
    crop.w = p.x - crop.x;
    crop.h = p.y - crop.y;
    redraw();
  }

  function endDrag() {
    dragging = false;
  }

  function applyCrop() {
    if (!crop || Math.abs(crop.w) < 10 || Math.abs(crop.h) < 10) return message.warning('请拖选裁剪区域');
    const source = editorImage;
    if (!source) return;
    const x = Math.round(Math.min(crop.x, crop.x + crop.w));
    const y = Math.round(Math.min(crop.y, crop.y + crop.h));
    const w = Math.round(Math.abs(crop.w));
    const h = Math.round(Math.abs(crop.h));
    const next = document.createElement('canvas');
    next.width = w;
    next.height = h;
    next.getContext('2d')!.drawImage(source, x, y, w, h, 0, 0, w, h);
    updateEditorBase(canvasToBase64(next));
  }

  function rotate(deg: number) {
    const img = editorImage;
    if (!img) return;
    const rightAngle = Math.abs(deg) % 180 === 90;
    const next = document.createElement('canvas');
    next.width = rightAngle ? img.naturalHeight : img.naturalWidth;
    next.height = rightAngle ? img.naturalWidth : img.naturalHeight;
    const ctx = next.getContext('2d')!;
    ctx.translate(next.width / 2, next.height / 2);
    ctx.rotate((deg * Math.PI) / 180);
    ctx.drawImage(img, -img.naturalWidth / 2, -img.naturalHeight / 2);
    updateEditorBase(canvasToBase64(next));
  }

  function resetEditor() {
    crop = null;
    if (options.getInitialBase64) {
      const initial = options.getInitialBase64();
      if (initial) {
        editorBase = initial;
        editorImage = null;
        loadEditorImage();
        return;
      }
    }
    redraw();
  }

  async function saveImage() {
    try {
      await options.onSave(editorBase);
      editorOpen.value = false;
    } catch (error: unknown) {
      const err = error as Error;
      message.error(err?.message || '保存失败');
    }
  }

  return {
    editorOpen,
    canvasEl,
    openEditor,
    startDrag,
    onDrag,
    endDrag,
    applyCrop,
    rotate,
    resetEditor,
    saveImage
  };
}
