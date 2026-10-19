import os
import shutil

BASE = "/Users/ferney/.gemini/antigravity-ide/scratch/isapet/backend/src/main/java/com/isapet"

def move_files(src_dir, dest_dir):
    src_path = os.path.join(BASE, src_dir)
    dest_path = os.path.join(BASE, dest_dir)
    if not os.path.exists(src_path): return
    for f in os.listdir(src_path):
        if f.endswith('.java'):
            shutil.move(os.path.join(src_path, f), os.path.join(dest_path, f))

# Move things around
move_files('controller', 'infrastructure/web/controller')
move_files('dto', 'infrastructure/web/dto')
move_files('exception', 'infrastructure/web/exception')
move_files('repository', 'infrastructure/persistence/repository')

# Delete old dirs
for d in ['controller', 'dto', 'entity', 'exception', 'repository', 'service', 'validation']:
    path = os.path.join(BASE, d)
    if os.path.exists(path):
        shutil.rmtree(path)

# Rename Repositories
old_af_repo = os.path.join(BASE, 'infrastructure/persistence/repository/AfiliadoRepository.java')
new_af_repo = os.path.join(BASE, 'infrastructure/persistence/repository/SpringDataAfiliadoRepository.java')
if os.path.exists(old_af_repo): os.rename(old_af_repo, new_af_repo)

old_ma_repo = os.path.join(BASE, 'infrastructure/persistence/repository/MascotaRepository.java')
new_ma_repo = os.path.join(BASE, 'infrastructure/persistence/repository/SpringDataMascotaRepository.java')
if os.path.exists(old_ma_repo): os.rename(old_ma_repo, new_ma_repo)

def replace_in_file(path, replacements):
    with open(path, 'r') as f:
        content = f.read()
    for old, new in replacements:
        content = content.replace(old, new)
    with open(path, 'w') as f:
        f.write(content)

# Update imports in Web
replacements = [
    ("package com.isapet.controller;", "package com.isapet.infrastructure.web.controller;"),
    ("package com.isapet.dto;", "package com.isapet.infrastructure.web.dto;"),
    ("package com.isapet.exception;", "package com.isapet.infrastructure.web.exception;"),
    ("package com.isapet.repository;", "package com.isapet.infrastructure.persistence.repository;"),
    ("import com.isapet.dto.", "import com.isapet.infrastructure.web.dto."),
    ("import com.isapet.exception.", "import com.isapet.infrastructure.web.exception."),
    ("import com.isapet.service.", "import com.isapet.application.service."),
    ("import com.isapet.entity.", "import com.isapet.infrastructure.persistence.entity."),
    ("AfiliadoRepository", "SpringDataAfiliadoRepository"),
    ("MascotaRepository", "SpringDataMascotaRepository"),
    ("public interface SpringDataAfiliadoRepository", "import com.isapet.infrastructure.web.dto.AfiliadoListaResponse;\\n\\npublic interface SpringDataAfiliadoRepository"),
    ("import com.isapet.infrastructure.persistence.entity.Afiliado;", "import com.isapet.infrastructure.persistence.entity.AfiliadoJpaEntity;"),
    ("import com.isapet.infrastructure.persistence.entity.Mascota;", "import com.isapet.infrastructure.persistence.entity.MascotaJpaEntity;"),
    ("<Afiliado, Long>", "<AfiliadoJpaEntity, Long>"),
    ("<Mascota, Long>", "<MascotaJpaEntity, Long>")
]

for root, _, files in os.walk(BASE):
    for file in files:
        if file.endswith(".java"):
            replace_in_file(os.path.join(root, file), replacements)

print("Backend moved and imports updated.")
