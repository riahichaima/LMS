import { Pipe, PipeTransform } from '@angular/core';

/**
 * Markdown simple pour les textes de l'IA (TP, corrections, chatbot) : blocs ```code```,
 * `code`, **gras**, titres ###, listes. Le HTML du texte est échappé avant la mise en forme.
 */
@Pipe({ name: 'markdown', standalone: true })
export class MarkdownPipe implements PipeTransform {
  transform(text: string | null | undefined): string {
    if (!text) return '';
    const parts = text.split(/```([\w+-]*)\n?([\s\S]*?)```/g);
    let html = '';
    for (let i = 0; i < parts.length; i += 3) {
      html += this.renderText(parts[i]);
      if (i + 2 < parts.length) {
        const lang = parts[i + 1];
        html += `<pre class="md-code"${lang ? ` data-lang="${escape(lang)}"` : ''}><code>${escape(parts[i + 2].replace(/\n$/, ''))}</code></pre>`;
      }
    }
    return html;
  }

  private renderText(text: string): string {
    const lines = text.split('\n');
    const out: string[] = [];
    let list: 'ul' | 'ol' | null = null;
    const closeList = () => { if (list) { out.push(`</${list}>`); list = null; } };

    for (const raw of lines) {
      const line = raw.trimEnd();
      const heading = line.match(/^(#{1,4})\s+(.*)$/);
      const bullet = line.match(/^\s*[-*•]\s+(.*)$/);
      const numbered = line.match(/^\s*\d+[.)]\s+(.*)$/);
      if (heading) {
        closeList();
        out.push(`<h6 class="md-heading">${inline(heading[2])}</h6>`);
      } else if (bullet || numbered) {
        const type = bullet ? 'ul' : 'ol';
        if (list !== type) { closeList(); out.push(`<${type}>`); list = type; }
        out.push(`<li>${inline((bullet || numbered)![1])}</li>`);
      } else if (line.trim() === '') {
        closeList();
      } else {
        closeList();
        out.push(`<p>${inline(line)}</p>`);
      }
    }
    closeList();
    return out.join('');
  }
}

function escape(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function inline(s: string): string {
  return escape(s)
    .replace(/`([^`]+)`/g, '<code class="md-inline">$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
}
