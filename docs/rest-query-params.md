# REST Query Params

This page explains how to use HTTP query parameters to filter, sort, and paginate data when querying metadata records via the REST API.

The filtering system is based on **field name suffixes** appended to a query parameter key. Each suffix maps to a specific SQL condition applied in the database `WHERE` clause.

---

## Pagination & Sorting

These are reserved parameters that control the result set shape.

| Parameter | Description | Example |
|---|---|---|
| `_limit` | Maximum number of records to return | `_limit=10` |
| `_start` | Offset (zero-based) of the first record to return | `_start=20` |
| `_sort` | Sort field and direction (`field:asc` or `field:desc`) | `_sort=created_at:desc` |
| `select_fields` | Comma-separated list of fields to include in the response | `select_fields=id,name,email` |

**Example — paginated and sorted request:**
```
GET /api/datalist/metadata/articles?_limit=10&_start=0&_sort=published_at:desc
```

---

## Filter Suffixes

Append a suffix to the **field name** to specify the type of SQL condition to generate.

### Equality (default)

No suffix needed. A plain field name matches records where the field equals the given value.

| Suffix | SQL operator | Description |
|---|---|---|
| *(none)* | `=` | Exact match |

**Example:**
```
GET /api/datalist/metadata/articles?status=published
```
→ `WHERE status = 'published'`

---

### Comparison

| Suffix | SQL operator | Description |
|---|---|---|
| `_ne` | `!=` | Not equal |
| `_lt` | `<` | Less than |
| `_lte` | `<=` | Less than or equal |
| `_gt` | `>` | Greater than |
| `_gte` | `>=` | Greater than or equal |

**Examples:**
```
GET /api/datalist/metadata/products?price_lt=100
```
→ `WHERE price < 100`

```
GET /api/datalist/metadata/products?price_gte=50&price_lte=200
```
→ `WHERE price >= 50 AND price <= 200`

```
GET /api/datalist/metadata/orders?status_ne=cancelled
```
→ `WHERE status != 'cancelled'`

---

### LIKE / Pattern matching

| Suffix | SQL operator | Value wrapping | Description |
|---|---|---|---|
| `_contains` | `LIKE` | `%value%` | Contains substring (case-sensitive) |
| `_like` | `LIKE` | `%value%` | Alias of `_contains` |
| `_ilike` | `ILIKE` | `%value%` | Contains substring (case-insensitive, PostgreSQL) |
| `_containss` | `LIKE lower()` | `%lower(value)%` | Contains lowercase substring (lowercases both sides) |
| `_rlike` | `lower(field) LIKE lower(?)` | `value%` | Starts with value (case-insensitive) |
| `_llike` | `lower(field) LIKE lower(?)` | `%value` | Ends with value (case-insensitive) |
| `_ncontains` | `NOT LIKE` | `%value%` | Does **not** contain substring |

**Examples:**

For `_rlike` and `_llike`, right/left refers to the position of the `%` wildcard.
Both sides are lowercased by the database for case-insensitive matching on PostgreSQL, MySQL, and H2.
These filters previously used case-sensitive `LIKE` without lowercasing; wildcard directions are unchanged.

```
GET /api/datalist/metadata/articles?title_contains=spring
```
→ `WHERE title LIKE '%spring%'`

```
GET /api/datalist/metadata/users?email_ilike=gmail
```
→ `WHERE email ILIKE '%gmail%'`

```
GET /api/datalist/metadata/products?code_rlike=SKU
```
→ `WHERE lower(code) LIKE lower(?)`, with bound parameter `SKU%`

```
GET /api/datalist/metadata/logs?message_ncontains=debug
```
→ `WHERE message NOT LIKE '%debug%'`

---

### IN list

| Suffix | SQL operator | Description |
|---|---|---|
| `_in` | `IN (...)` | Field value is one of a comma-separated list |

**Example:**
```
GET /api/datalist/metadata/articles?status_in=draft,published,review
```
→ `WHERE status IN ('draft', 'published', 'review')`

---

### NULL checks

These suffixes do **not** require a value — the parameter key alone is sufficient.

| Suffix | SQL condition | Description |
|---|---|---|
| `_nn` | `IS NOT NULL` | Field has a value |
| `_inn` | `IS NULL` | Field has no value |

**Examples:**
```
GET /api/datalist/metadata/users?avatar_nn
```
→ `WHERE avatar IS NOT NULL`

```
GET /api/datalist/metadata/users?deleted_at_inn
```
→ `WHERE deleted_at IS NULL`

---

### Empty string checks

These suffixes do **not** require a value — the parameter key alone is sufficient.

| Suffix | SQL condition | Description |
|---|---|---|
| `_nie` | `<> ''` | Field is **not** an empty string |
| `_ie` | `= ''` | Field **is** an empty string |

**Examples:**
```
GET /api/datalist/metadata/articles?description_nie
```
→ `WHERE description <> ''`

```
GET /api/datalist/metadata/profiles?bio_ie
```
→ `WHERE bio = ''`

---

## Numeric field filtering

When filtering on a numeric column (e.g. `numeric`, `integer`, `bigint`, `decimal`), pass the value as a plain string — the API automatically casts it to the correct type before binding the prepared statement.

**Example** — table with column `heat_number numeric(10,0)`:
```
GET /api/ficr_results?heat_number=5
```
→ `WHERE heat_number = 5` (bound as a number, not a string)

No explicit cast is needed in the query parameter.

---

## Combining multiple filters

All filter parameters are combined with `AND`.

```
GET /api/datalist/metadata/products?category=electronics&price_gte=100&price_lte=500&status_ne=discontinued&name_ilike=pro
```
→
```sql
WHERE category = 'electronics'
  AND price >= 100
  AND price <= 500
  AND status != 'discontinued'
  AND name ILIKE '%pro%'
```

---

## Quick Reference

| Suffix | SQL | Notes |
|---|---|---|
| *(none)* | `field = ?` | Exact equality |
| `_ne` | `field != ?` | |
| `_lt` | `field < ?` | |
| `_lte` | `field <= ?` | |
| `_gt` | `field > ?` | |
| `_gte` | `field >= ?` | |
| `_contains` | `field LIKE '%?%'` | Case-sensitive |
| `_like` | `field LIKE '%?%'` | Alias of `_contains` |
| `_ilike` | `field ILIKE '%?%'` | Case-insensitive (PostgreSQL) |
| `_containss` | `lower(field) LIKE '%lower(?)%'` | Both sides lowercased |
| `_rlike` | `lower(field) LIKE lower(?)` | Starts with; bind `value%`; case-insensitive |
| `_llike` | `lower(field) LIKE lower(?)` | Ends with; bind `%value`; case-insensitive |
| `_ncontains` | `field NOT LIKE '%?%'` | |
| `_in` | `field IN (?,?,?)` | Comma-separated values |
| `_nn` | `field IS NOT NULL` | No value needed |
| `_inn` | `field IS NULL` | No value needed |
| `_nie` | `field <> ''` | No value needed |
| `_ie` | `field = ''` | No value needed |
