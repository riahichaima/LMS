import { Directive, ElementRef, effect, input } from '@angular/core';

/** Lie un flux vidéo/audio (MediaStream) à une balise <video> ou <audio>. */
@Directive({ selector: 'video[appSrcObject], audio[appSrcObject]', standalone: true })
export class SrcObjectDirective {
  appSrcObject = input<MediaStream | null | undefined>(null);

  constructor(el: ElementRef<HTMLMediaElement>) {
    effect(() => {
      const stream = this.appSrcObject() ?? null;
      const media = el.nativeElement;
      if (media.srcObject !== stream) {
        media.srcObject = stream;
        if (stream) void media.play().catch(() => {});
      }
    });
  }
}
