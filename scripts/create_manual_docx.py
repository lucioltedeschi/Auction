from pathlib import Path
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.enum.section import WD_SECTION
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "Manual_Flujos_y_Pruebas_AuctIO.docx"
NAVY = "071827"
GOLD = "A8872F"
CREAM = "F3F0E8"
PALE = "E8EEF5"
MUTED = "475569"
GREEN = "166534"
RED = "991B1B"


def set_font(run, size=None, color=NAVY, bold=None, italic=None):
    run.font.name = "Calibri"
    run._element.get_or_add_rPr().rFonts.set(qn("w:ascii"), "Calibri")
    run._element.get_or_add_rPr().rFonts.set(qn("w:hAnsi"), "Calibri")
    if size is not None: run.font.size = Pt(size)
    if color: run.font.color.rgb = RGBColor.from_string(color)
    if bold is not None: run.bold = bold
    if italic is not None: run.italic = italic


def shade(cell, fill):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = tcPr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tcPr.append(shd)
    shd.set(qn("w:fill"), fill)


def cell_width(cell, dxa):
    tcPr = cell._tc.get_or_add_tcPr()
    tcW = tcPr.find(qn("w:tcW"))
    if tcW is None:
        tcW = OxmlElement("w:tcW"); tcPr.append(tcW)
    tcW.set(qn("w:w"), str(dxa)); tcW.set(qn("w:type"), "dxa")


def no_split(row):
    trPr = row._tr.get_or_add_trPr()
    trPr.append(OxmlElement("w:cantSplit"))


def repeat_header(row):
    trPr = row._tr.get_or_add_trPr()
    trPr.append(OxmlElement("w:tblHeader"))


def set_table_geometry(table, widths):
    table.autofit = False
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    tblPr = table._tbl.tblPr
    tblW = tblPr.find(qn("w:tblW"))
    if tblW is None: tblW = OxmlElement("w:tblW"); tblPr.append(tblW)
    tblW.set(qn("w:w"), str(sum(widths))); tblW.set(qn("w:type"), "dxa")
    tblInd = OxmlElement("w:tblInd"); tblInd.set(qn("w:w"), "120"); tblInd.set(qn("w:type"), "dxa"); tblPr.append(tblInd)
    grid = table._tbl.tblGrid
    for child in list(grid): grid.remove(child)
    for width in widths:
        col = OxmlElement("w:gridCol"); col.set(qn("w:w"), str(width)); grid.append(col)
    for row in table.rows:
        no_split(row)
        for idx, cell in enumerate(row.cells):
            cell_width(cell, widths[idx])
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            tcMar = cell._tc.get_or_add_tcPr().find(qn("w:tcMar"))
            if tcMar is None:
                tcMar = OxmlElement("w:tcMar"); cell._tc.get_or_add_tcPr().append(tcMar)
            for edge, val in (("top",80),("bottom",80),("start",120),("end",120)):
                node = OxmlElement("w:"+edge); node.set(qn("w:w"), str(val)); node.set(qn("w:type"), "dxa"); tcMar.append(node)


def table(doc, headers, rows, widths):
    t = doc.add_table(rows=1, cols=len(headers)); t.style = "Table Grid"
    for i, h in enumerate(headers):
        shade(t.rows[0].cells[i], PALE)
        p = t.rows[0].cells[i].paragraphs[0]; p.paragraph_format.space_after = Pt(0)
        set_font(p.add_run(h), 9, NAVY, True)
    repeat_header(t.rows[0])
    for ridx, values in enumerate(rows):
        cells = t.add_row().cells
        for i, value in enumerate(values):
            if ridx % 2: shade(cells[i], "F8FAFC")
            p = cells[i].paragraphs[0]; p.paragraph_format.space_after = Pt(0); p.paragraph_format.line_spacing = 1.0
            set_font(p.add_run(str(value)), 8.6, NAVY)
    set_table_geometry(t, widths)
    return t


def heading(doc, text, level=1):
    p = doc.add_paragraph(style=f"Heading {level}")
    p.paragraph_format.keep_with_next = True
    set_font(p.add_run(text), 16 if level == 1 else 13 if level == 2 else 12,
             GOLD if level == 1 else NAVY, True)
    return p


def para(doc, text, bold_prefix=None):
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(6); p.paragraph_format.line_spacing = 1.25
    if bold_prefix and text.startswith(bold_prefix):
        set_font(p.add_run(bold_prefix), 10.5, NAVY, True)
        set_font(p.add_run(text[len(bold_prefix):]), 10.5, NAVY)
    else: set_font(p.add_run(text), 10.5, NAVY)
    return p


def bullet(doc, text):
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.left_indent = Inches(.375); p.paragraph_format.first_line_indent = Inches(-.188)
    p.paragraph_format.space_after = Pt(4); p.paragraph_format.line_spacing = 1.25
    set_font(p.add_run(text), 10.5, NAVY)


def numbered_group(doc, items):
    numbering = doc.part.numbering_part.element
    abstract_ids = [int(x.get(qn("w:abstractNumId"))) for x in numbering.findall(qn("w:abstractNum"))]
    num_ids = [int(x.get(qn("w:numId"))) for x in numbering.findall(qn("w:num"))]
    abstract_id = max(abstract_ids, default=0) + 1
    num_id = max(num_ids, default=0) + 1

    abstract = OxmlElement("w:abstractNum"); abstract.set(qn("w:abstractNumId"), str(abstract_id))
    multi = OxmlElement("w:multiLevelType"); multi.set(qn("w:val"), "singleLevel"); abstract.append(multi)
    lvl = OxmlElement("w:lvl"); lvl.set(qn("w:ilvl"), "0")
    start = OxmlElement("w:start"); start.set(qn("w:val"), "1"); lvl.append(start)
    fmt = OxmlElement("w:numFmt"); fmt.set(qn("w:val"), "decimal"); lvl.append(fmt)
    text = OxmlElement("w:lvlText"); text.set(qn("w:val"), "%1."); lvl.append(text)
    suff = OxmlElement("w:suff"); suff.set(qn("w:val"), "tab"); lvl.append(suff)
    ppr = OxmlElement("w:pPr")
    tabs = OxmlElement("w:tabs"); tab = OxmlElement("w:tab"); tab.set(qn("w:val"), "num"); tab.set(qn("w:pos"), "540"); tabs.append(tab); ppr.append(tabs)
    ind = OxmlElement("w:ind"); ind.set(qn("w:left"), "540"); ind.set(qn("w:hanging"), "270"); ppr.append(ind)
    lvl.append(ppr); abstract.append(lvl); numbering.append(abstract)
    num = OxmlElement("w:num"); num.set(qn("w:numId"), str(num_id))
    aid = OxmlElement("w:abstractNumId"); aid.set(qn("w:val"), str(abstract_id)); num.append(aid); numbering.append(num)

    for item in items:
        p = doc.add_paragraph()
        p.paragraph_format.space_after = Pt(4); p.paragraph_format.line_spacing = 1.25
        num_pr = OxmlElement("w:numPr")
        ilvl = OxmlElement("w:ilvl"); ilvl.set(qn("w:val"), "0")
        nid = OxmlElement("w:numId"); nid.set(qn("w:val"), str(num_id))
        num_pr.append(ilvl); num_pr.append(nid); p._p.get_or_add_pPr().append(num_pr)
        set_font(p.add_run(item), 10.5, NAVY)


def callout(doc, title, text, color=GOLD):
    t = doc.add_table(rows=1, cols=1); set_table_geometry(t, [9360]); shade(t.cell(0,0), CREAM)
    p = t.cell(0,0).paragraphs[0]
    set_font(p.add_run(title + "\n"), 10, color, True)
    set_font(p.add_run(text), 9.5, NAVY)


def flow(doc, title, actor, objective, preconditions, steps, expected, negatives=None, page_break=True):
    h = heading(doc, title, 1)
    if page_break: h.paragraph_format.page_break_before = True
    table(doc, ["Actor", "Objetivo"], [[actor, objective]], [1700, 7660])
    heading(doc, "Precondiciones", 2)
    for item in preconditions: bullet(doc, item)
    heading(doc, "Ejecución", 2)
    numbered_group(doc, steps)
    heading(doc, "Resultado esperado", 2)
    for item in expected: bullet(doc, item)
    if negatives:
        heading(doc, "Controles negativos", 2)
        for item in negatives: bullet(doc, item)


doc = Document()
sec = doc.sections[0]
sec.page_width = Inches(8.5); sec.page_height = Inches(11)
sec.top_margin = sec.bottom_margin = sec.left_margin = sec.right_margin = Inches(1)
sec.header_distance = sec.footer_distance = Inches(.492)

normal = doc.styles["Normal"]
normal.font.name = "Calibri"; normal.font.size = Pt(11)
normal.paragraph_format.space_after = Pt(6); normal.paragraph_format.line_spacing = 1.25
for name, size, before, after in (("Heading 1",16,18,10),("Heading 2",13,14,7),("Heading 3",12,10,5)):
    s = doc.styles[name]; s.font.name="Calibri"; s.font.size=Pt(size); s.font.bold=True
    s.font.color.rgb=RGBColor.from_string(GOLD if name=="Heading 1" else NAVY)
    s.paragraph_format.space_before=Pt(before); s.paragraph_format.space_after=Pt(after); s.paragraph_format.keep_with_next=True

header = sec.header
hp = header.paragraphs[0]; hp.alignment = WD_ALIGN_PARAGRAPH.LEFT
set_font(hp.add_run("AUCT.IO  |  GUÍA DE OPERACIÓN Y PRUEBAS"), 8.5, MUTED, True)
footer = sec.footer
fp = footer.paragraphs[0]; fp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
set_font(fp.add_run("Entrega 3  ·  Grupo 15  ·  "), 8.5, MUTED)
fld = OxmlElement("w:fldSimple"); fld.set(qn("w:instr"), "PAGE"); fp._p.append(fld)

# Editorial cover
logo = ROOT / "docs" / "logo.png"
if logo.exists():
    p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    logo_shape = p.add_run().add_picture(str(logo), width=Inches(1.15))
    logo_shape._inline.docPr.set("descr", "Logotipo de Auct.io con martillo de subastas")
p = doc.add_paragraph(); p.alignment=WD_ALIGN_PARAGRAPH.CENTER; p.paragraph_format.space_before=Pt(42)
set_font(p.add_run("MANUAL DE DEMOSTRACIÓN"), 11, GOLD, True)
p = doc.add_paragraph(); p.alignment=WD_ALIGN_PARAGRAPH.CENTER
set_font(p.add_run("Auct.io"), 31, NAVY, True)
p = doc.add_paragraph(); p.alignment=WD_ALIGN_PARAGRAPH.CENTER
set_font(p.add_run("Flujos funcionales, controles y plan de pruebas"), 15, MUTED)
p = doc.add_paragraph(); p.alignment=WD_ALIGN_PARAGRAPH.CENTER; p.paragraph_format.space_before=Pt(60)
set_font(p.add_run("Android + Node.js + Azure SQL + Render"), 11, NAVY, True)
p = doc.add_paragraph(); p.alignment=WD_ALIGN_PARAGRAPH.CENTER
set_font(p.add_run("Revisión integral · 27 de junio de 2026 · versión final"), 10, MUTED)
callout(doc, "PROPÓSITO", "Ejecutar una demo reproducible de punta a punta y verificar cada regla crítica de la tercera entrega.")

doc.add_page_break(); heading(doc, "1. Inicio rápido", 1)
para(doc, "Este documento está pensado para quien presenta, prueba o corrige la aplicación. Cada flujo indica precondiciones, acciones, resultado esperado y controles negativos.")
heading(doc, "Entorno", 2)
table(doc, ["Componente", "Referencia"], [
    ["Aplicación", "APK Android de Auct.io"],
    ["API", "https://auct-io-api.onrender.com"],
    ["Persistencia", "Azure SQL — datos de demo XL"],
    ["Administrador", "Documento 20000111 · clave 1234"],
    ["Cliente principal", "Documento 30123456 · clave 1234"],
    ["Cliente alternativo", "Documento 30999888 · clave 1234"],
    ["Cliente bloqueado", "Documento 31888777 · clave 1234"],
], [2300,7060])
callout(doc, "IMPORTANTE", "La instancia gratuita de Render puede tardar hasta 60 segundos en despertar. Esperar la primera respuesta antes de concluir que hay una falla.")
heading(doc, "Recorrido sugerido de 15 minutos", 2)
for x in ["Ingresar como cliente y mostrar Inicio, Estadísticas y Avisos.", "Abrir Fotografía & Tecnología y mostrar cuatro relojes e historiales independientes.", "Pujar por dos lotes distintos y comprobar la actualización WebSocket en un segundo teléfono.", "Comprobar ambos lotes en Historial.", "Mostrar una compra y elegir el medio de pago.", "Consignar un producto con seis fotos.", "Ingresar como administrador, leer indicadores y resolver fichas sin escribir IDs.", "Cerrar con adjudicación, pago y controles negativos."]: bullet(doc,x)

heading(doc, "Secuencia maestra punta a punta", 2)
numbered_group(doc, [
    "Acceso cliente (30123456 / 1234): comprobar Inicio, categoría, medios y avisos.",
    "Tiempo real: abrir el mismo catálogo en dos equipos y comparar los cuatro lotes.",
    "Puja: ofertar y comprobar confirmación, historial y reinicio del reloj.",
    "Consignación: enviar un bien con seis fotos y verificar el estado pendiente.",
    "Revisión admin (20000111 / 1234): leer KPIs, badges y resolver la ficha.",
    "Catálogo: asignar el producto aceptado por nombre a una subasta.",
    "Cierre: adjudicar un lote con mejor oferta y generar la compra.",
    "Pago: volver como ganador, elegir un medio compatible y acreditar.",
    "Trazabilidad: contrastar Historial, Estadísticas y Avisos.",
])

flow(doc, "2. Registro y verificación de identidad", "Cliente nuevo + administrador",
     "Crear una cuenta verificable y asignarle categoría.",
     ["Usar un documento y correo no existentes.", "Disponer de imágenes legibles del frente y dorso del DNI."],
     ["Completar datos personales y avanzar al segundo paso.", "Adjuntar ambas caras del documento y finalizar.", "Comprobar la pantalla de verificación pendiente.", "Ingresar como administrador y tocar la ficha del usuario.", "Revisar identidad, contacto y documentación; aprobar y elegir categoría."],
     ["El cliente queda admitido y recibe un aviso.", "La categoría asignada condiciona qué subastas puede pujar."],
     ["Rechazar documentación incompleta.", "Intentar iniciar sesión antes de la admisión: debe impedirse."],
     page_break=False)

flow(doc, "3. Medios de pago", "Cliente + administrador", "Registrar, verificar y mantener varios medios.",
     ["Cliente admitido y sesión iniciada."],
     ["Abrir Medios de pago y crear una cuenta, tarjeta o cheque.", "Elegir moneda y completar entidad/referencia.", "Ingresar como administrador y tocar la ficha pendiente.", "Revisar titular, entidad, referencia, moneda y compatibilidad; aprobar en un toque.", "Volver como cliente y comprobar el estado verificado."],
     ["El medio queda disponible para pujar y pagar.", "Cada alta, edición y decisión genera un aviso."],
     ["Un medio editado vuelve a revisión.", "Una tarjeta no internacional no habilita una subasta en dólares.", "Un cheque no permite superar la garantía disponible."])

flow(doc, "4. Catálogo, puja y varios artículos", "Cliente comprador", "Ofertar por más de un lote y visualizar cada participación con su propio reloj.",
     ["Cliente admitido, sin multas ni compras vencidas.", "Medio verificado compatible con la moneda.", "Categoría igual o superior a la subasta."],
     ["Abrir Fotografía & Tecnología o cualquier subasta disponible.", "Recorrer todos los lotes abiertos; cada tarjeta debe mostrar su reloj y las últimas tres ofertas.", "Anotar la duración configurada y pujar por un primer lote.", "Confirmar que sólo ese reloj vuelve a la duración completa y que la oferta aparece primera en el mini historial.", "Sin salir de la subasta, pujar por otro lote abierto del mismo catálogo.", "Comprobar que mejores ofertas, relojes y liderazgos cambian solos en dos teléfonos.", "Abrir Historial y verificar dos fichas independientes actualizadas automáticamente.", "Tocar cada ficha para volver a su subasta."],
     ["Puede haber varios lotes recibiendo pujas simultáneamente dentro del catálogo.", "Cada lote conserva contador, historial, oferta propia, mejor actual y estado GANADO/LIDERANDO/SUPERADA.", "WebSocket sincroniza el catálogo y se reconecta si se corta la red.", "La puja genera un aviso y nunca reemplaza a otra participación."],
     ["Probar menos de mejor oferta + 1% de base: rechazo.", "Probar más de mejor oferta + 20% de base en categorías no premium: rechazo.", "Cambiar de subasta debe permitirse antes de ofertar y bloquearse, con explicación, después de una puja."])

flow(doc, "5. Adjudicación y pago", "Ganador + administrador", "Cerrar el lote, generar la compra y elegir cómo abonarla.",
     ["Lote activo con al menos una oferta.", "Ganador con un medio verificado compatible."],
     ["Desde el panel interno elegir Cerrar lote.", "Seleccionar la ficha que muestra artículo, pujas y mejor oferta.", "Confirmar adjudicación.", "Ingresar como ganador y abrir Compras.", "Tocar ELEGIR MEDIO Y PAGAR.", "Seleccionar uno de los medios compatibles y confirmar."],
     ["La venta conserva importe, comisión, envío, moneda y medio elegido.", "El estado pasa a pagado y aparece un aviso de acreditación."],
     ["Un cheque insuficiente debe rechazarse.", "Un medio de otro cliente o no verificado debe rechazarse.", "Tras 72 h impago, el cliente no puede volver a pujar."])

flow(doc, "6. Consignación de un producto", "Cliente vendedor + administrador", "Ingresar un bien, negociar condiciones e incorporarlo a una subasta.",
     ["Cliente admitido.", "Al menos seis fotos del artículo.", "Descripción, procedencia y declaración de propiedad."],
     ["Abrir Consignar y completar toda la ficha.", "Adjuntar seis o más fotos sin deformación.", "Enviar a inspección.", "Como administrador tocar la ficha, revisar toda la información y enviar precio base/comisión.", "Como cliente aceptar la propuesta.", "Como administrador elegir Asignar a subasta, seleccionar producto y destino por nombre y confirmar."],
     ["El producto recorre estados auditables y cada cambio crea un aviso.", "Solo un producto aceptado por su titular puede incorporarse al catálogo."],
     ["Con menos de seis fotos debe impedirse el envío.", "Un rechazo requiere motivo.", "No debe asignarse un producto todavía pendiente."])

flow(doc, "7. Operación administrativa", "Empleado", "Resolver pendientes y administrar subastas sin conocer IDs.",
     ["Ingresar con 20000111 / 1234."],
     ["Leer el bloque ejecutivo: clientes habilitados, subastas activas, lotes y pagos pendientes.", "Comprobar los badges numéricos de cada acción.", "Tocar Revisar usuarios, Medios de pago o Consignaciones; la pantalla debe desplazarse directamente a la cola correspondiente.", "Tocar una ficha para ver toda la información y aprobar o rechazar.", "Gestionar altas, modificaciones, cancelaciones y bajas de subastas.", "Asignar productos mediante selectores descriptivos.", "Cerrar lotes desde la lista de lotes abiertos.", "Aplicar multa desde la lista de impagos vencidos; confirmar el 10% calculado."],
     ["No se solicita copiar IDs técnicos.", "Los indicadores y colas se refrescan al volver al panel.", "Cada decisión muestra confirmación estándar y actualiza pendientes."],
     ["Una subasta con dependencias no debe eliminarse.", "No debe ofrecerse multa antes de 72 horas ni duplicar una pendiente."])

flow(doc, "8. Avisos y estadísticas", "Cliente", "Consultar trazabilidad y actividad personal.",
     ["Haber realizado al menos una acción relevante."],
     ["Abrir Avisos y revisar los eventos ordenados.", "Marcar un aviso como leído.", "Abrir Estadísticas.", "Comparar subastas, lotes, pujas, ganados, consignados y avisos.", "Revisar barras por categoría y actividad reciente."],
     ["Las métricas coinciden con Historial y Compras.", "Las acciones relevantes quedan persistidas, no solo mostradas en un popup."],
     ["Un usuario sin actividad debe ver estados vacíos claros, no una pantalla rota."])

doc.add_page_break(); heading(doc, "9. Matriz de pruebas negativas", 1)
table(doc, ["Caso", "Acción", "Resultado esperado"], [
    ["N01", "Pujar sin medio verificado", "Bloqueo con explicación y acceso a medios de pago."],
    ["N02", "Pujar con categoría inferior", "Bloqueo por categoría insuficiente."],
    ["N03", "Pujar con multa pendiente", "Bloqueo hasta regularización."],
    ["N04", "Pujar con compra vencida >72 h", "Bloqueo por incumplimiento."],
    ["N05", "Puja menor al mínimo", "HTTP 400; no insertar oferta."],
    ["N06", "Puja mayor al máximo no premium", "HTTP 400; mostrar máximo permitido."],
    ["N07", "Pagar con moneda incompatible", "No ofrecer el medio o rechazarlo en servidor."],
    ["N08", "Consignar con menos de 6 fotos", "No crear producto."],
    ["N09", "Asignar producto no aceptado", "Conflicto; no crear lote."],
    ["N10", "Cerrar lote sin ofertas", "Compra automática por la empresa al precio base."],
    ["N11", "Entrar a dos subastas", "Mantener una sola sesión activa."],
    ["N12", "Eliminar subasta con dependencias", "Rechazo y conservación de datos."],
], [900, 3300, 5160])

doc.add_page_break(); heading(doc, "10. Cumplimiento de la consigna", 1)
rows = [
    ["Identidad y categoría", "Cumple", "Registro en dos pasos, DNI, revisión y categoría."],
    ["Pagos", "Cumple", "Múltiples medios, verificación, moneda, cheque y selección al pagar."],
    ["Pujas", "Cumple", "Tiempo real, reglas 1%/20%, confirmación y trazabilidad."],
    ["Adjudicación", "Cumple", "Ganador, comisión, envío, retiro y compra sin ofertas."],
    ["Impagos", "Cumple", "72 h, bloqueo y multa automática sugerida del 10%."],
    ["Consignación", "Cumple", "6 fotos, propiedad, inspección, propuesta y aceptación."],
    ["Administración", "Cumple", "Información completa y decisiones sin IDs manuales."],
    ["Panel ejecutivo", "Cumple", "KPIs operativos, badges de trabajo e iconografía específica por acción."],
    ["Estadísticas", "Cumple", "Participaciones, pujas, categorías, ganados e importes."],
    ["Video", "Fuera de alcance", "La consigna lo excluye expresamente."],
    ["Servicios externos", "Representados", "Póliza, correo y fondos reales requieren proveedor productivo."],
]
table(doc, ["Área", "Estado", "Verificación"], rows, [1900,1500,5960])
para(doc, "Las integraciones de aseguradora, correo transaccional, banco adquirente y transferencia al consignante se modelan mediante estados y avisos persistentes. En un despliegue productivo deben conectarse a proveedores con credenciales reales; la entrega no procesa dinero ni emite pólizas reales.")

doc.add_page_break(); heading(doc, "11. Checklist visual y de cierre", 1)
for x in [
    "El encabezado ocupa una franja compacta y no empuja el contenido fuera de pantalla.",
    "Ninguna fotografía se estira: se usa recorte proporcional en tarjetas y ajuste contenido en galerías.",
    "Todos los popups usan fondo, iconos, tipografía y acciones consistentes con Auct.io.",
    "Los iconos administrativos representan su acción, tienen tamaño uniforme y descripción accesible.",
    "Botones principales tienen altura táctil suficiente y estados habilitado/deshabilitado visibles.",
    "Listas vacías explican qué falta hacer.",
    "Los formularios validan antes de enviar y el backend vuelve a validar reglas económicas.",
    "Las operaciones exitosas actualizan la pantalla y generan aviso cuando corresponde.",
    "Probar en teléfono real: inicio, rotación bloqueada si aplica, teclado, scroll y retorno.",
    "Confirmar API /api/health y esperar el despertar de Render antes de la demo.",
    "Usar datos demo; no compartir credenciales reales de Azure SQL en capturas o repositorios.",
]: bullet(doc, x)
callout(doc, "CRITERIO DE APROBACIÓN", "La demo se considera lista cuando los ocho flujos principales y los doce controles negativos arrojan los resultados esperados sin cierres de la app, contenido recortado ni datos inconsistentes.", GREEN)

OUT.parent.mkdir(parents=True, exist_ok=True)
doc.save(OUT)
print(OUT)
