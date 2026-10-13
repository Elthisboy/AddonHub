# Prueba de humo del proxy ya desplegado.
# Uso:  .\smoke-test.ps1 -Base https://addonhub-proxy.TU-USUARIO.workers.dev -Token TU_APP_TOKEN
param(
    [Parameter(Mandatory = $true)][string]$Base,
    [Parameter(Mandatory = $true)][string]$Token
)

$ok = $true
function Check($name, $expected, $actual) {
    $pass = ($actual -eq $expected)
    if (-not $pass) { $script:ok = $false }
    "{0} {1}: esperado {2}, obtenido {3}" -f ($(if ($pass) { "✅" } else { "❌" }), $name, $expected, $actual)
}
function Status($method, $path, $token, $body) {
    $args = @("-s", "-o", "NUL", "-w", "%{http_code}", "-X", $method, "$Base$path")
    if ($token) { $args += @("-H", "X-App-Token: $token") }
    if ($body) { $args += @("-H", "Content-Type: application/json", "-d", $body) }
    & curl.exe @args
}

Check "sin token"                 401 (Status GET  "/v1/categories" $null $null)
Check "token incorrecto"          401 (Status GET  "/v1/categories" "mal" $null)
Check "categorías"                200 (Status GET  "/v1/categories" $Token $null)
Check "búsqueda"                  200 (Status GET  "/v1/mods/search?classId=4984&searchFilter=dragon&pageSize=5" $Token $null)
Check "búsqueda parámetro malo"   400 (Status GET  "/v1/mods/search?pageSize=9999" $Token $null)
Check "búsqueda fuera de rango"   400 (Status GET  "/v1/mods/search?index=9990&pageSize=50" $Token $null)
Check "varios mods"               200 (Status POST "/v1/mods" $Token '{"modIds":[1504044]}')
Check "varios mods inválido"      400 (Status POST "/v1/mods" $Token '{"modIds":"x"}')
Check "destacados"                200 (Status POST "/v1/mods/featured" $Token '{}')
Check "descarga: host ajeno"      400 (Status GET  "/dl?u=https%3A%2F%2Fexample.com%2Fa.mcaddon" $Token $null)
Check "descarga: extensión mala"  400 (Status GET  "/dl?u=https%3A%2F%2Fedge.forgecdn.net%2Ffiles%2F1%2F2%2Fa.exe" $Token $null)
Check "ruta desconocida"          404 (Status GET  "/v1/games" $Token $null)

if ($ok) { "`nTodo bien." } else { "`nHay fallos: revisa arriba."; exit 1 }
