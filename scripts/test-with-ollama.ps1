$ErrorActionPreference = 'Stop'
$compose = Join-Path $PSScriptRoot '..\docker-compose.yml'
$compose = [IO.Path]::GetFullPath($compose)
$started = $false
$previousOllamaHost = $env:OLLAMA_HOST

try {
    docker compose -f $compose up -d ollama
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the Ollama service. Check that Docker is running.' }
    $started = $true

    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        docker compose -f $compose exec -T ollama ollama list *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'Ollama did not become ready within 120 seconds.' }

    foreach ($model in @('qwen2.5-coder:7b', 'deepseek-r1')) {
        Write-Host "Ensuring local model is available: $model"
        docker compose -f $compose exec -T ollama ollama pull $model
        if ($LASTEXITCODE -ne 0) { throw "Could not download Ollama model $model." }
    }

    $env:OLLAMA_HOST = 'http://localhost:11434'
    mvn -B -pl platform-cli -am test '-Dtestng.suiteXmlFile=src/test/resources/ollama-testng.xml'
    if ($LASTEXITCODE -ne 0) { throw 'The Ollama TestNG integration suite failed.' }
}
finally {
    if ($null -eq $previousOllamaHost) {
        Remove-Item Env:OLLAMA_HOST -ErrorAction SilentlyContinue
    } else {
        $env:OLLAMA_HOST = $previousOllamaHost
    }
    if ($started) {
        docker compose -f $compose stop ollama
    }
}
