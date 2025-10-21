-- === JACKPOT CONFIGS ===
insert into jackpot_configs (jackpot_config_id, name)
values
  ('fixed-fixed',       'Fixed 20% contribution / Fixed 20% chance'),
  ('fixed-variable',    'Fixed 20% contribution / Variable 10%→100% chance'),
  ('variable-variable', 'Variable 30%→10% contribution / Variable 10%→100% chance'),
  ('variable-fixed',    'Variable 30%→10% contribution / Fixed 20% chance');

-- === CONFIG ENTRIES ===
insert into config_entries (jackpot_config_id, slot, strategy_key, config_json)
values
  ('fixed-fixed',       'CONTRIBUTION', 'FIXED',    '{"percent":"20.0","scale":"2"}'),
  ('fixed-fixed',       'REWARD',       'FIXED',    '{"chancePercent":"20.0"}'),

  ('fixed-variable',    'CONTRIBUTION', 'FIXED',    '{"percent":"20.0","scale":"2"}'),
  ('fixed-variable',    'REWARD',       'VARIABLE', '{"startPercent":"10.0","endPercent":"100.0","fromPool":"0.00","toPool":"100000.00","scale":"2"}'),

  ('variable-variable', 'CONTRIBUTION', 'VARIABLE', '{"startPercent":"30.0","endPercent":"10.0","fromPool":"0.00","toPool":"100000.00","scale":"2"}'),
  ('variable-variable', 'REWARD',       'VARIABLE', '{"startPercent":"10.0","endPercent":"100.0","fromPool":"0.00","toPool":"100000.00","scale":"2"}'),

  ('variable-fixed',    'CONTRIBUTION', 'VARIABLE', '{"startPercent":"30.0","endPercent":"10.0","fromPool":"0.00","toPool":"100000.00","scale":"2"}'),
  ('variable-fixed',    'REWARD',       'FIXED',    '{"chancePercent":"20.0"}');

-- === JACKPOTS  ===
insert into jackpots (name, initial_amount, current_amount, cycle, version, jackpot_config_id)
values
  ('Jackpot FIXED/FIXED',        10000.00, 10000.00, 0, 0, 'fixed-fixed'),
  ('Jackpot FIXED/VARIABLE',     10000.00, 10000.00, 0, 0, 'fixed-variable'),
  ('Jackpot VARIABLE/VARIABLE',  10000.00, 10000.00, 0, 0, 'variable-variable'),
  ('Jackpot VARIABLE/FIXED',     10000.00, 10000.00, 0, 0, 'variable-fixed');




