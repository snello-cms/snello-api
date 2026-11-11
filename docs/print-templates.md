# Print templates

A print template turns the rows of a SQL query into a file. Rows live in `printtemplates`
(`/api/printtemplates`, admin page *Print Templates*; writes need Admin or Manager).

| Field | Meaning |
|---|---|
| `name` | letters, digits, `_`, `-`; part of the print URL |
| `export_type` | `pdf`, `html`, `csv`, `xlsx` |
| `sql_query` | the rows; columns are read by their alias |
| `html_body` | Qute template for `pdf` / `html` (required) and `csv` (optional: empty = every column, `,`-separated) |
| `template_file`, `template_file_name` | `xlsx` only: a JXLS template, base64 (stored in the row, so it moves with export/import) |
| `api_protected`, `username_field` | same row-level security as select queries |

## Printing

```
GET /api/printtemplates/{name}/print?city=Rome&total_gte=100&_sort=number:asc
```

- Every parameter not starting with `_` filters the query result by column alias, with the data API operators
  (`_ne`, `_gt`, `_gte`, `_lt`, `_lte`, `_contains`, `_in`, ...). The query is wrapped, so its own WHERE / GROUP BY
  is untouched.
- `_sort=col:asc|desc`, `_inline=true` (show in the browser instead of downloading), `_separator=;` (automatic CSV).
- Protected template: anonymous → 401; users other than Admin / Manager get only their rows (the owner filter cannot
  be overridden by a parameter).
- The response carries `Content-Disposition` (`{name}.{ext}`) and `X-Snello-Rows`. Template or SQL errors answer
  `400 {"error": "..."}`.
- At most `snello.print.max-rows` rows (default 50000) are read.

## Template data

Qute (`pdf`, `html`, `csv`) and JXLS (`xlsx`) get the same data:

| Name | Value |
|---|---|
| `rows` | list of rows, each a map column alias → value |
| `params` | first value of each filter parameter (`params.city`) |
| `count` | number of rows |
| `now` | `yyyy-MM-dd HH:mm` |
| `template` | template name |

Qute in `pdf` / `html` HTML-escapes every value. A missing column (`{row.nope}`) is an error, not an empty string.

```html
<html><head><style>@page { size: A4; margin: 15mm } td { border: 1px solid #999 }</style></head><body>
<h1>Invoices {params.city ?: 'all cities'}</h1>
<table>{#for row in rows}<tr><td>{row.number}</td><td>{row.customer}</td><td>{row.total}</td></tr>{/for}</table>
<p>{count} invoices, printed {now}</p>
</body></html>
```

PDF uses openhtmltopdf: HTML is parsed leniently (jsoup), CSS 2.1 plus `@page`; no flexbox or grid.

JXLS (`xlsx`): comment on A1 `jx:area(lastCell="C2")`, comment on the first data cell
`jx:each(items="rows" var="row" lastCell="C2")`, cells `${row.number}`, `${row.customer}`, ...

Save the template from Excel or LibreOffice. Files written by some generators (e.g. Python openpyxl) store text as
inline strings, and JXLS drops static inline-string cells such as a header row; opening and re-saving the file in
Excel / LibreOffice fixes it.

## Native image

PDFBox and POI need extra GraalVM configuration (AWT fonts, reflection). The all-in-one image is JVM; the native
control build has not been tried with print templates.
