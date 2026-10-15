"""Record exact upstream template hashes and known malformed/optional NBT paths."""
import argparse,hashlib,json,zipfile,tomllib
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--audit',type=Path,required=True);p.add_argument('--mods',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
catalog=[]
for finding in json.loads(a.audit.read_text())['findings']:
 edits=[]
 for path,value in finding['issues']:
  if not isinstance(value,str):continue
  if value=='minecraft:air':
   parent=path.rsplit('/',1)[0]
   edits.append({'path':parent,'operation':'remove' if parent.endswith('/buyB') else 'empty','expectedId':value,'repair':'structures.explicit_air_stacks'})
  elif value=='alexsmobs:moose_ribs':
   edits.append({'path':path.rsplit('/',1)[0],'operation':'empty','expectedId':value,'unlessItem':value,'repair':'structures.optional_template_items'})
  elif value=='guardvillagers:guard':
   parent=path.removesuffix('/nbt/id') if path.endswith('/nbt/id') else path.rsplit('/',1)[0]
   edits.append({'path':parent,'operation':'remove','expectedId':value,'unlessEntity':value,'repair':'structures.optional_template_entities'})
 if not edits:continue
 with zipfile.ZipFile(a.mods/finding['jar']) as z:
  raw=z.read(finding['resource']);assert hashlib.sha256(raw).hexdigest()==finding['sha256']
  metadata=next((name for name in ['META-INF/neoforge.mods.toml','META-INF/mods.toml'] if name in z.namelist()),None)
  if metadata:owners=[mod['modId'] for mod in tomllib.loads(z.read(metadata).decode())['mods']]
  elif 'fabric.mod.json' in z.namelist():owners=[json.loads(z.read('fabric.mod.json'))['id']]
  else:raise ValueError('No mod metadata: '+finding['jar'])
 namespace,resource=finding['resource'].removeprefix('data/').split('/',1)
 if namespace in owners:owner=namespace
 elif len(owners)==1:owner=owners[0]
 else:raise ValueError('Ambiguous owning mod: '+finding['jar'])
 catalog.append({'resource':namespace+':'+resource,'mod':owner,'sha256':finding['sha256'],'edits':edits})
a.output.write_text(json.dumps(catalog,indent=2)+'\n')
print(json.dumps({'resources':len(catalog),'edits':sum(len(row['edits']) for row in catalog),'bytes':a.output.stat().st_size}))
