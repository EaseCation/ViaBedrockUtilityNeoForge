#!/usr/bin/env bash
set -euo pipefail

dependency_root="$PWD/build/ci-dependencies"
mkdir -p "$dependency_root"
export GIT_LFS_SKIP_SMUDGE=1

while read -r name repository revision; do
    target="$dependency_root/$name"
    test ! -e "$target"
    git init -q "$target"
    git -C "$target" remote add origin "$repository"
    git -C "$target" fetch --depth 1 origin "$revision"
    git -c advice.detachedHead=false -C "$target" checkout --detach FETCH_HEAD
    test "$(git -C "$target" rev-parse HEAD)" = "$revision"
    printf '%s %s\n' "$name" "$revision" >> "$dependency_root/revisions.txt"
done <<'DEPENDENCIES'
BedrockMotion https://github.com/EaseCation/BedrockMotion.git 53ec0ce07d1d36459bf34dab8ce22abce78c355f
CubeConverter https://github.com/EaseCation/CubeConverter.git 6b4f5b1dfc9deefe23809a8b89e9d4425de34bdd
Mocha https://github.com/EaseCation/mocha.git ed2c48cfb3250df3b6ad01957053bc11da5eacf4
BECameraNeoForge git@github.com:EaseCation/BECameraNeoForge.git df3cfd74ab25e66c55fe83905229f940c598999e
BEParticleNeoForge git@github.com:EaseCation/BEParticleNeoForge.git 19215b2ba74e505fbedad7af12a815e8525331da
ec-deploy-assets git@github.com:EaseCation/ec-deploy-assets.git 40188d3a3633dd53dd330e530f0fba2cf4e9edcc
DEPENDENCIES

git -C "$dependency_root/ec-deploy-assets" lfs install --local
git -C "$dependency_root/ec-deploy-assets" lfs pull --include='resource-packs/CodeFunCore/ec_hub.zip,resource-packs/CodeFunCore/ec_ze.zip,resource-packs/CodeFunCore/rl_defense.zip,resource-packs/CodeFunCore/rl_defense_ec_entity.zip,resource-packs/CodeFunCore/ec_gun_res.zip,resource-packs/CodeFunCore/ec_rl.zip,bedrock-loader-packs/vanilla.zip'

python3 - <<'PY'
from pathlib import Path
from zipfile import is_zipfile

assets = Path('build/ci-dependencies/ec-deploy-assets')
for relative in (
    'resource-packs/CodeFunCore/ec_hub.zip',
    'resource-packs/CodeFunCore/ec_ze.zip',
    'resource-packs/CodeFunCore/rl_defense.zip',
    'resource-packs/CodeFunCore/rl_defense_ec_entity.zip',
    'resource-packs/CodeFunCore/ec_gun_res.zip',
    'resource-packs/CodeFunCore/ec_rl.zip',
    'bedrock-loader-packs/vanilla.zip',
):
    path = assets / relative
    if not is_zipfile(path):
        raise SystemExit(f'Missing test ZIP or unresolved Git LFS pointer: {path}')
PY
