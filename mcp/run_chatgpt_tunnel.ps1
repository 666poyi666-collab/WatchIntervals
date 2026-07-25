$runner = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'run_persistent_chatgpt_tunnel.ps1'
& $runner
