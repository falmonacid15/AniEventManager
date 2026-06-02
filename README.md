# 🎮 AniEventManager Placeholders

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-Plugin-green?style=for-the-badge">
  <img src="https://img.shields.io/badge/PlaceholderAPI-Supported-blue?style=for-the-badge">
  <img src="https://img.shields.io/badge/TAB-Integration-purple?style=for-the-badge">
  <img src="https://img.shields.io/badge/FancyHolograms-Compatible-orange?style=for-the-badge">
</p>

---

## 📖 Descripción

**AniEventManager** expone una gran cantidad de placeholders a través de PlaceholderAPI para mostrar información en tiempo real de eventos, equipos y minijuegos.

Compatible con:
- TAB (scoreboard, bossbar, tablist)
- FancyHolograms
- Cualquier plugin que soporte PlaceholderAPI

---

## 📚 Navegación

- Convención
- Equipos
- Ranking
- TNT Run
- Bingo
- Frozen Heist
- Boat Racing
- Parkour Duos
- TeamID dinámico
- Integración con TAB
- Uso con FancyHolograms

---

## 🧩 Convención

Todos los placeholders usan:

%anievent_<placeholder>%

---

## 👥 Equipos

| Valor | Descripción |
|------|------------|
| %anievent_has_team% | Indica si el jugador tiene equipo |
| %anievent_team_name% | Nombre del equipo |
| %anievent_team_id% | ID del equipo |
| %anievent_team_color% | Color del equipo |
| %anievent_team_members% | Miembros |
| %anievent_team_size% | Tamaño |
| %anievent_team_isfull% | Si está lleno |
| %anievent_team_score% | Puntaje |
| %anievent_team_rank% | Ranking |
| %anievent_teams_players_total% | Total de jugadores |
| %anievent_teams_with_players% | Equipos activos |

---

## 🏆 Ranking

| Valor | Descripción |
|------|------------|
| %anievent_top_1_name% → %anievent_top_8_name% | Nombre del equipo |
| %anievent_top_1_score% → %anievent_top_8_score% | Puntaje |
| %anievent_top_1_color% | Color |
| %anievent_top_1_members% | Miembros |

---

## 💣 TNT Run

### Global

| Valor | Descripción |
|------|------------|
| %anievent_tntrun_running% | Minijuego activo |
| %anievent_tntrun_state% | Estado |
| %anievent_tntrun_players% | Jugadores |
| %anievent_tntrun_teams% | Equipos vivos |
| %anievent_tntrun_elapsed% | Tiempo transcurrido |
| %anievent_tntrun_floor_total% | Total pisos |

### Jugador

| Valor | Descripción |
|------|------------|
| %anievent_tntrun_iseliminated% | Eliminado |
| %anievent_tntrun_floor% | Piso actual |
| %anievent_tntrun_floor_players% | Jugadores en piso |
| %anievent_tntrun_floor_playernames% | Nombres |
| %anievent_tntrun_floor_blocks% | Bloques restantes |
| %anievent_tntrun_floor_blocks_total% | Bloques totales |
| %anievent_tntrun_floor_blocks_percent% | Porcentaje |

### Doble salto

| Valor | Descripción |
|------|------------|
| %anievent_tntrun_jump_cooldown% | Cooldown |
| %anievent_tntrun_jump_bar% | Barra visual |

---

## 🎯 Bingo

| Valor | Descripción |
|------|------------|
| %anievent_bingo_running% | Activo |
| %anievent_bingo_time% | Tiempo |
| %anievent_bingo_timepercent% | % tiempo |
| %anievent_bingo_percent% | Progreso |
| %anievent_bingo_done% | Completados |
| %anievent_bingo_total% | Total |
| %anievent_bingo_progressbar% | Barra |

---

## ❄️ Frozen Heist

| Valor | Descripción |
|------|------------|
| %anievent_fh_running% | Activo |
| %anievent_fh_state% | Estado |
| %anievent_fh_time% | Tiempo |
| %anievent_fh_timepercent% | % tiempo |
| %anievent_fh_score% | Puntaje |
| %anievent_fh_rank% | Ranking |
| %anievent_fh_flag_state% | Estado bandera |
| %anievent_fh_flag_carrier% | Portador |
| %anievent_fh_carrying% | Lleva bandera |
| %anievent_fh_carrying_team% | Equipo bandera |
| %anievent_fh_frozen% | Congelado |
| %anievent_fh_frozen_seconds% | Tiempo congelado |

---

## 🚤 Boat Racing

| Valor | Descripción |
|------|------------|
| %anievent_br_running% | Activo |
| %anievent_br_state% | Estado |
| %anievent_br_totallaps% | Vueltas totales |
| %anievent_br_lap% | Vuelta |
| %anievent_br_laps% | Formato vueltas |
| %anievent_br_laptime% | Tiempo vuelta |
| %anievent_br_bestlap% | Mejor vuelta |
| %anievent_br_lastlap% | Última vuelta |
| %anievent_br_position% | Posición |
| %anievent_br_position_num% | Posición numérica |
| %anievent_br_qualytime% | Tiempo qualy |
| %anievent_br_gridpos% | Posición salida |
| %anievent_br_finished% | Terminó |
| %anievent_br_racepos% | Posición final |
| %anievent_br_gap% | Diferencia delante |
| %anievent_br_interval% | Diferencia líder |

---

## 🏃 Parkour Duos

| Valor | Descripción |
|------|------------|
| %anievent_pd_running% | Activo |
| %anievent_pd_state% | Estado |
| %anievent_pd_time% | Tiempo |
| %anievent_pd_checkpoint% | Checkpoint |
| %anievent_pd_checkpoints% | Total checkpoints |
| %anievent_pd_progress% | Progreso |
| %anievent_pd_players_in_cp% | Jugadores en checkpoint |
| %anievent_pd_finished% | Finalizado |
| %anievent_pd_rank% | Ranking |
| %anievent_pd_score% | Score |

---

## ⚙️ TAB

Usa los placeholders directamente en:

- Scoreboards
- Bossbars
- Tablist

Ejemplo incluido en config del plugin.

---

## 🧊 FancyHolograms

Compatible directamente usando placeholders:

/hologram lines add example "%anievent_team_name%"
