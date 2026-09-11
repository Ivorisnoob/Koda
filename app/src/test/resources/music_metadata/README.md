# Music metadata fixtures

Sanitized public WEB_REMIX responses probed in September 2026 with `hl=en`,
`gl=US`, no account cookies. Only parser inputs are retained: display text,
public content identifiers, endpoint types, artwork and track layout. Tracking,
menu commands, continuation tokens and account data are omitted.

- `search_song`: BIRDS OF A FEATHER, `WKZO-CWeOVA`.
- `collaboration_search` / `collaboration_next`: lovely, `MMfpp0-lnw4`.
  Khalid occupies the text position the old parser treated as the album.
- `playlist_track`: SKINNY from the HIT ME HARD AND SOFT playlist; album is in
  its own flex column, not the artist column.
- `search_next_song`: a row from a live `musicShelfContinuation` search reply.
- `ep_search`, `ep_multi_artist_search`: the rest and boygenius EP search rows.
- `ep_album`: the rest, `MPREb_moznxmUWnGr`, header and four track rows.
- `single_album`: BILLIE EILISH., `MPREb_Y4KWN0glLx2`, header and one track row.
- `album_card`, `ep_card`, `single_card`: the record, the rest and The Parting
  Glass from the boygenius artist page.
- `artist_header`: the Billie Eilish immersive header: bio, monthly audience,
  banner.
- `artist_top_song`: BIRDS OF A FEATHER top-songs row with artist and album
  links in separate flex columns.
- `similar_shelf` / `featured_shelf`: Fans might also like and Featured on
  carousels, trimmed to two entries each.
- `album_header`: HIT ME HARD AND SOFT responsive header plus two track rows
  with empty artist columns (attribution comes from the header strapline).
- `next_panel`: the requested song's own `playlistPanelVideoRenderer` from a
  music `/next` reply; its byline runs name artist, album and year.

Header/track fixtures omit recommendation carousels. Tests separately inject
out-of-shelf rows, missing links, conflicting page types, duplicate tracks and
same-titled releases to exercise failure cases without a network connection.
