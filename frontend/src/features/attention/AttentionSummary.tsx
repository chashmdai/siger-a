import type {AttentionData} from './schema';
import {optionLabel} from './schema';
import {fullName} from './adult/adultForm.ts';
import {Callout, Facts} from '../../components/Primitives';
import {Icon} from '../../components/Icon';

/** Resumen de lectura: primer contacto y continuidad prevista, con las alertas de cuidado visibles. */
export function AttentionSummary({form}: {form: AttentionData}) {
  const contact = String(form.safe_contact), note = String(form.safety_note);
  const careful = contact === 'NO_CONTACTAR' || contact === 'VERIFICAR' || note !== '';
  return <div className="attention-summary">
    {careful && <Callout tone="warn" title={contact === 'NO_CONTACTAR' ? 'No contactar por estos medios' : contact === 'VERIFICAR' ? 'Verificar antes de contactar' : 'Indicaciones de contacto seguro'}>
      {note && <p className="preserve">{note}</p>}
    </Callout>}
    {form.priority === 'PRIORITARIA' && <Callout tone="warn" title="Necesidad de atención prioritaria observada">
      {String(form.priority_note) ? <p className="preserve">{String(form.priority_note)}</p> : <p>No cambia la complejidad del REQ ni activa protocolos automáticos.</p>}
    </Callout>}
    <div className="summary-grid">
      <section className="summary-card">
        <h3><Icon name="message" size={18}/>Motivo inicial</h3>
        <blockquote className="preserve">{String(form.motive) || 'Sin motivo registrado.'}</blockquote>
        <Facts items={[
          ['Persona requirente', (form.modality === 'NNA' ? '' : fullName(form)) || String(form.presenter_name) || 'Sin identificar'],
          ['Modalidad', form.modality === 'NNA' ? 'Escucha directa a NNA' : 'General o persona adulta'],
          ['Contacto', optionLabel('safe_contact', form.safe_contact) || 'Por verificar'],
          ['Personas relacionadas', form.people.length === 0 ? 'Ninguna agregada' : form.people.length === 1 ? '1 persona' : `${form.people.length} personas`],
        ]}/>
      </section>
      <section className="summary-card">
        <h3><Icon name="flow" size={18}/>Continuidad prevista</h3>
        <p className="summary-course">{form.course === 'SEGUIMIENTO' ? 'Necesita un Caso de seguimiento por jefatura.' : 'Primer contacto en curso o por definir.'}</p>
        <h4>Gestiones comprometidas</h4>
        <p className="preserve">{String(form.commitments) || 'Sin compromisos consignados.'}</p>
        <p className="muted small">Los datos faltantes pueden completarse en una nueva versión.</p>
      </section>
    </div>
  </div>;
}
