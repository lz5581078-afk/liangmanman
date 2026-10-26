"""
核对前端调用的接口和后端路由是否一一对应。
用法：python3 scripts/check_routes.py .   （在项目根目录）
输出里 request.js 的 '/api*' 和 admin api/index.js 的 '/api/admin*' 是请求封装本身，属于正常；
其他条目都要检查（三元表达式拼出的地址也会被列出，人工确认即可）。
"""
import re,os,sys
root=sys.argv[1]
routes=set()
for d,_,fs in os.walk(root+'/backend/src/main/java'):
    for f in fs:
        if not f.endswith('Controller.java'): continue
        s=open(os.path.join(d,f)).read()
        base=''
        m=re.search(r'@RequestMapping\("([^"]+)"\)\s*public class',s)
        if m: base=m.group(1)
        for mm in re.finditer(r'@(Get|Post|Put|Delete)Mapping\(\{?"([^"]*)"(?:,\s*"([^"]*)")?',s):
            for p in (mm.group(2),mm.group(3)):
                if p is None: continue
                path=re.sub(r'\{[^}]+\}','*',base+p)
                routes.add((mm.group(1).upper(),path))
def norm(u):
    u=u.split('?')[0]
    u=re.sub(r'\$\{[^}]+\}','*',u)
    return u
def check(dirn,pat,prefix='/api'):
    bad=[]
    for d,_,fs in os.walk(dirn):
        if 'node_modules' in d or 'dist' in d or 'unpackage' in d: continue
        for f in fs:
            if not f.endswith(('.js','.vue','.ts')): continue
            s=open(os.path.join(d,f)).read()
            for m in re.finditer(pat,s):
                meth=m.group(1).upper()
                expr=m.group(2)
                # 拼接表达式：字符串段保留，变量段变 *
                parts=re.findall(r"[`'\"]([^`'\"]*)[`'\"]|([A-Za-z_][\w.]*(?:\([^)]*\))?)",expr)
                url=''.join(a if a or not b else '*' for a,b in parts)
                url=norm(url)
                if not url.startswith(prefix): url=prefix+url
                if meth in ('DEL',): meth='DELETE'
                if meth.startswith('UPLOAD'): meth='POST'
                ok=any(r[0]==meth and re.fullmatch(re.escape(r[1]).replace(r'\*','[^/]+'),url) for r in routes)
                if not ok: bad.append((f,meth,url))
    return bad
print('routes',len(routes))
print('MINIAPP', check(root+'/miniapp/src', r"\b(get|post|put|del|delete|upload|uploadTo)\(\s*((?:[`'\"][^`'\"]*[`'\"]|[\w.]+(?:\([^)]*\))?)(?:\s*\+\s*(?:[`'\"][^`'\"]*[`'\"]|[\w.]+(?:\([^)]*\))?))*)"))
print('ADMIN', check(root+'/admin/src', r"\b(get|post|put|del|delete|upload)\(\s*((?:[`'\"][^`'\"]*[`'\"]|[\w.]+(?:\([^)]*\))?)(?:\s*\+\s*(?:[`'\"][^`'\"]*[`'\"]|[\w.]+(?:\([^)]*\))?))*)", '/api/admin'))
