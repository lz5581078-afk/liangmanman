"""
演示数据：4 家店、约 18 个商品（含同一款在 3 家店）、凑单、订单、评价、求购、赊账、轮播图。
前提：后端以 dev 配置跑在 http://127.0.0.1:8080，且是空库（每次重启内存库后执行一次）。
依赖：Python 3 + Pillow（pip install pillow），中文字体 Noto Sans CJK（路径见 FONT）。
演示用的品牌、条码、登记证号都是虚构的。
用法：python3 scripts/demo_seed.py
"""
import urllib.parse
import json, io, uuid, urllib.request, random
from PIL import Image, ImageDraw, ImageFont

B = 'http://127.0.0.1:8080'
FONT = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
LAT, LNG = 34.7466, 113.6253
random.seed(7)


def req(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(B + path, data=data, method=method)
    r.add_header('Content-Type', 'application/json')
    if token:
        r.add_header('Authorization', 'Bearer ' + token)
    res = json.loads(urllib.request.urlopen(r).read().decode())
    if res['code'] != 0:
        raise RuntimeError(path + ' ' + json.dumps(res, ensure_ascii=False))
    return res.get('data')


def upload(token, img):
    buf = io.BytesIO()
    img.save(buf, 'JPEG', quality=88)
    boundary = uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="a.jpg"\r\n'
            'Content-Type: image/jpeg\r\n\r\n').encode() + buf.getvalue() + f'\r\n--{boundary}--\r\n'.encode()
    r = urllib.request.Request(B + '/api/files', data=body, method='POST')
    r.add_header('Content-Type', 'multipart/form-data; boundary=' + boundary)
    r.add_header('Authorization', 'Bearer ' + token)
    return json.loads(urllib.request.urlopen(r).read().decode())['data']['url']


def font(size):
    return ImageFont.truetype(FONT, size)


def bag(title, sub, color, kind='bag'):
    """画一张像商品实拍的包装图"""
    W = 600
    im = Image.new('RGB', (W, W), (236, 232, 222))
    d = ImageDraw.Draw(im)
    # 地面阴影
    d.ellipse((90, 500, 510, 560), fill=(214, 208, 196))
    if kind == 'bag':
        d.rounded_rectangle((120, 70, 480, 530), 40, fill=color)
        d.rectangle((120, 70, 480, 110), fill=tuple(max(0, c - 30) for c in color))
        d.rounded_rectangle((150, 190, 450, 400), 18, fill=(255, 255, 255))
    elif kind == 'bottle':
        d.rounded_rectangle((250, 60, 350, 130), 12, fill=(60, 60, 60))
        d.rounded_rectangle((180, 120, 420, 530), 50, fill=color)
        d.rounded_rectangle((200, 220, 400, 420), 14, fill=(255, 255, 255))
    elif kind == 'box':
        d.polygon([(130, 160), (300, 90), (470, 160), (300, 230)], fill=tuple(min(255, c + 30) for c in color))
        d.polygon([(130, 160), (300, 230), (300, 540), (130, 470)], fill=color)
        d.polygon([(300, 230), (470, 160), (470, 470), (300, 540)], fill=tuple(max(0, c - 35) for c in color))
        d.rectangle((150, 270, 290, 400), fill=(255, 255, 255))
    elif kind == 'roll':
        d.ellipse((110, 180, 230, 460), fill=tuple(max(0, c - 30) for c in color))
        d.rectangle((170, 180, 470, 460), fill=color)
        d.ellipse((410, 180, 530, 460), fill=(250, 250, 250))
        d.ellipse((450, 290, 490, 350), fill=(160, 160, 160))
    cx = {'bag': 300, 'bottle': 300, 'box': 220, 'roll': 300}[kind]
    cy = {'bag': 260, 'bottle': 290, 'box': 315, 'roll': 300}[kind]
    tsize = 48 if len(title) <= 5 else 40
    if kind == 'box':
        tsize = 26
    tf = font(tsize)
    tw = d.textlength(title, font=tf)
    tcol = (255, 255, 255) if kind == 'roll' else (40, 40, 40)
    if kind == 'roll':
        tcol = (40, 40, 40)
    d.text((cx - tw / 2, cy - tsize / 2 - 8), title, font=tf, fill=tcol if kind != 'roll' else (255, 255, 255))
    sf = font(24 if kind != 'box' else 16)
    sw = d.textlength(sub, font=sf)
    d.text((cx - sw / 2, cy + tsize / 2 + 8), sub, font=sf, fill=color if kind != 'roll' else (255, 255, 255))
    return im


def storefront(name, color):
    W, H = 800, 500
    im = Image.new('RGB', (W, H), (205, 215, 225))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 380, W, H), fill=(170, 165, 155))
    d.rectangle((60, 90, 740, 390), fill=(245, 242, 235))
    d.rectangle((40, 60, 760, 160), fill=color)
    f = font(56)
    tw = d.textlength(name, font=f)
    d.text((400 - tw / 2, 72), name, font=f, fill=(255, 240, 200))
    d.rectangle((300, 190, 500, 390), fill=(90, 110, 120))
    for x in (90, 540):
        d.rectangle((x, 200, x + 170, 330), fill=(160, 190, 205))
    # 门口堆的肥料袋
    for i, c in enumerate([(30, 120, 70), (200, 60, 50), (40, 90, 160)]):
        d.rounded_rectangle((520 + i * 60, 330, 575 + i * 60, 395), 8, fill=c)
    return im


def login(code):
    return req('POST', '/api/auth/wx-login', body={'code': code})['token']


# 包装上的身份信息（演示用的虚构品牌、条码、登记证号）
ID = {
    'fhf_a': dict(brand='演示牌', barcode='6901234567892', regNo='农肥（2024）准字0001号', manufacturer='演示肥业有限公司'),
    'fhf_a_reg': dict(brand='演示牌', regNo='农肥(2024)准字0001号'),          # 同一款：没拍到条码，靠登记证号 + 规格认出
    'fhf_b': dict(brand='示范牌', barcode='6902000000018'),
    'urea': dict(brand='演示牌', barcode='6903000000022'),
    'bcl': dict(brand='样品牌', regNo='PD20240001'),
    None: {},
}

STORES = [
    dict(code='demo-merchant', name='丰收农资', phone='13700000001', dlat=0.006, dlng=0.004, town='城关镇',
         addr='城关镇建设路 18 号', color=(170, 40, 30), notice='春耕备肥，复合肥满 10 袋送货到地头', hours='07:00-19:00',
         radius=15, minOrder=50, fee=0,
         products=[('复合肥 15-15-15', '50kg', 165, 1, 'fhf_a', ('复合肥', '演示牌 · 15-15-15 · 50kg', (30, 120, 70), 'bag'), 300, True, False, 0),
                   ('复合肥 17-17-17', '40kg', 142, 1, 'fhf_b', ('复合肥', '示范牌 · 17-17-17 · 40kg', (160, 40, 40), 'bag'), 150, True, False, 0),
                   ('尿素 含氮46%', '40kg', 118, 1, 'urea', ('尿素', '演示牌 · 含氮≥46% · 40kg', (40, 90, 160), 'bag'), 200, True, False, 0),
                   ('磷酸二铵', '50kg', 185, 1, None, ('磷酸二铵', '64% · 50kg', (200, 120, 30), 'bag'), 120, True, False, 0),
                   ('郑单958 玉米种子', '1kg', 28, 3, None, ('郑单958', '玉米种子 · 1kg', (220, 170, 30), 'box'), 500, True, True, 8),
                   ('吡虫啉 10%', '100g', 6.5, 2, 'bcl', ('吡虫啉', '样品牌 · 10% 可湿性粉剂', (120, 40, 140), 'bottle'), 800, True, True, 6),
                   ('地膜 0.01mm', '1卷', 45, 4, None, ('地膜', '0.01mm', (120, 160, 180), 'roll'), 60, True, False, 0)]),
    dict(code='demo-m2', name='绿野种业', phone='13700000002', dlat=-0.012, dlng=0.010, town='李庄乡',
         addr='李庄乡政府东 200 米', color=(30, 110, 60), notice='小麦良种到货，可代播', hours='06:30-20:00',
         radius=20, minOrder=0, fee=5,
         products=[('郑单958 玉米种子', '1kg', 26.5, 3, None, ('郑单958', '玉米种子 · 1kg', (220, 170, 30), 'box'), 300, True, True, 10),
                   ('百农207 小麦种子', '25kg', 95, 3, None, ('百农207', '小麦种子 · 25kg', (190, 140, 60), 'bag'), 80, True, False, 0),
                   ('复合肥 15-15-15', '50kg', 168, 1, 'fhf_a', ('复合肥', '演示牌 · 15-15-15 · 50kg', (30, 120, 70), 'bag'), 100, True, False, 0)]),
    dict(code='demo-m3', name='金穗农资', phone='13700000003', dlat=0.025, dlng=-0.018, town='王村镇',
         addr='王村镇十字路口', color=(180, 120, 20), notice='', hours='08:00-18:00',
         radius=8, minOrder=100, fee=10,
         products=[('尿素 含氮46%', '40kg', 115, 1, 'urea', ('尿素', '演示牌 · 含氮≥46% · 40kg', (40, 90, 160), 'bag'), 150, True, False, 0),
                   ('复合肥 15-15-15', '50kg', 162, 1, 'fhf_a_reg', ('复合肥', '演示牌 · 15-15-15 · 50kg', (30, 120, 70), 'bag'), 80, True, False, 0),
                   ('草甘膦 41%', '1L', 22, 2, None, ('草甘膦', '41% 水剂 · 1L', (30, 140, 60), 'bottle'), 200, True, True, 8),
                   ('有机肥', '40kg', 38, 1, None, ('有机肥', '有机质≥45%', (110, 80, 50), 'bag'), 400, True, False, 0)]),
    dict(code='demo-m4', name='惠农植保', phone='13700000004', dlat=-0.035, dlng=-0.03, town='张集镇',
         addr='张集镇农贸市场北门', color=(40, 80, 150), notice='飞防植保接单中', hours='07:30-18:30',
         radius=10, minOrder=0, fee=0,
         products=[('吡虫啉 10%', '100g', 6, 2, 'bcl', ('吡虫啉', '样品牌 · 10% 可湿性粉剂', (120, 40, 140), 'bottle'), 300, True, True, 5),
                   ('有机无机复合肥', '40kg', 98, 1, None, ('复合肥', '有机无机 · 40kg', (90, 110, 40), 'bag'), 60, True, False, 0),
                   ('多菌灵 50%', '500g', 12, 2, None, ('多菌灵', '50% 可湿性粉剂', (200, 60, 50), 'bottle'), 200, True, True, 5),
                   ('电动喷雾器 16L', '1台', 168, 4, None, ('喷雾器', '16L 电动', (230, 120, 20), 'box'), 20, True, True, 0)]),
]

stores = []
for s in STORES:
    t = login(s['code'])
    cover = upload(t, storefront(s['name'], s['color']))
    sid = req('POST', '/api/merchant/store', t, dict(name=s['name'], phone=s['phone'], coverImage=cover,
              lat=LAT + s['dlat'], lng=LNG + s['dlng'], addressText=s['addr'], town=s['town'], mainCategories='化肥 农药 种子'))['id']
    req('PUT', '/api/merchant/store', t, dict(notice=s['notice'] or None, businessHours=s['hours'], deliveryRadiusKm=s['radius'],
        minOrderAmount=s['minOrder'], deliveryFee=s['fee'], pickupEnabled=True, deliveryEnabled=True))
    pids = []
    for name, spec, price, cat, std, art, stock, local, express, efee in s['products']:
        img = upload(t, bag(*art))
        body = dict(images=[img], price=price, name=name, spec=spec, categoryId=cat, stock=stock,
                    localDelivery=local, expressDelivery=express, description=f'{name}，规格 {spec}，正品保证，量大可议价。')
        body.update(ID[std])
        if express:
            body['expressFee'] = efee
        pids.append(req('POST', '/api/merchant/products', t, body)['id'])
    stores.append(dict(token=t, id=sid, pids=pids))
    print('store', s['name'], sid, pids)

m = stores[0]
# 商家开通收款（模拟立即成功），这样能在线支付
for st in stores:
    a, b2 = [upload(st['token'], Image.new('RGB', (300, 200), (200, 200, 200))) for _ in range(2)]
# 收款开通需要私有图片接口
def upload_private(token):
    buf = io.BytesIO(); Image.new('RGB', (300, 200), (220, 220, 220)).save(buf, 'JPEG')
    boundary = uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="a.jpg"\r\nContent-Type: image/jpeg\r\n\r\n').encode() + buf.getvalue() + f'\r\n--{boundary}--\r\n'.encode()
    r = urllib.request.Request(B + '/api/merchant/onboarding/image', data=body, method='POST')
    r.add_header('Content-Type', 'multipart/form-data; boundary=' + boundary)
    r.add_header('Authorization', 'Bearer ' + token)
    return json.loads(urllib.request.urlopen(r).read().decode())['data']['imageId']

def valid_id():
    w = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
    body = '41010119800101123'
    return body + '10X98765432'[sum(int(c) * x for c, x in zip(body, w)) % 11]

for i, st in enumerate(stores):
    req('POST', '/api/merchant/onboarding', st['token'], dict(
        idCardFrontImageId=upload_private(st['token']), idCardBackImageId=upload_private(st['token']),
        idName=['王建国', '李秀英', '张卫东', '刘志强'][i], idNumber=valid_id(), idValidFrom='2015-01-01', idValidTo='长期',
        bankName='中国农业银行', bankCityCode='410100', bankAccount='62284800123456' + str(70 + i), mobile='1370000000' + str(i + 1)))

# 凑单：复合肥 50kg 凑 20 袋 148 元
gb = req('POST', '/api/merchant/group-buys', m['token'], dict(productId=m['pids'][0], groupPrice=148, targetQty=20, days=3, limitPerUser=10, endWhenReached=True))
print('group', gb['id'])

# 买家们
villages = ['前进村', '后王庄', '小李庄', '东赵村', '西杨村']
names = ['赵大哥', '孙师傅', '周婶', '吴老汉', '郑二叔']
buyers = []
for i in range(5):
    t = login(f'demo-buyer-{i}')
    aid = req('POST', '/api/addresses', t, dict(contactName=names[i], phone=f'1360000000{i}', lat=LAT + random.uniform(-0.03, 0.03),
              lng=LNG + random.uniform(-0.03, 0.03), village=villages[i], detail=f'{villages[i]} {i + 3} 组', isDefault=True))['id']
    buyers.append((t, aid))


def place(t, aid, items, fulfillment='DELIVERY', pay='COD', gid=None, store=None):
    body = dict(storeId=(store or m)['id'], items=[dict(productId=p, quantity=q) for p, q in items], fulfillment=fulfillment,
                payMethod=pay, addressId=aid if fulfillment != 'PICKUP' else None, contactName='买家', contactPhone='13600000099')
    if gid:
        body['groupBuyId'] = gid
    o = req('POST', '/api/orders', t, body)
    if pay == 'ONLINE':
        no = req('POST', f'/api/orders/{o["id"]}/pay', t)['outTradeNo']
        req('POST', f'/api/pay/mock/{no}/success', t)
    return o['id']


# 凑单参与 3 人共 13 袋
for (t, aid), q in zip(buyers[:3], [6, 4, 3]):
    place(t, aid, [(m['pids'][0], q)], pay='ONLINE', gid=gb['id'])

# 已完成并评价的订单
comments = [(5, '老板人实在，下午就送到地头了'), (5, '肥料是正品，价格比镇上便宜'), (4, '送货快，就是下雨天晚了一会儿')]
for (t, aid), (score, text) in zip(buyers[2:5], comments):
    oid = place(t, aid, [(m['pids'][1], 2), (m['pids'][4], 3)], pay='ONLINE')
    for a in ('accept', 'dispatch', 'delivered'):
        req('POST', f'/api/merchant/orders/{oid}/{a}', m['token'], {})
    req('POST', f'/api/orders/{oid}/confirm', t)
    r = req('POST', f'/api/orders/{oid}/review', t, dict(score=score, content=text))
req('POST', f'/api/merchant/reviews/{r["id"]}/reply', m['token'], dict(reply='下雨路滑耽误了，下次提前出发，感谢支持！'))

# 别的店也来几条评价
for st, score in [(stores[1], 5), (stores[2], 4)]:
    t, aid = buyers[0]
    oid = place(t, aid, [(st['pids'][0], 2)], pay='ONLINE', store=st)
    for a in ('accept', 'dispatch', 'delivered'):
        req('POST', f'/api/merchant/orders/{oid}/{a}', st['token'], {})
    req('POST', f'/api/orders/{oid}/confirm', t)
    req('POST', f'/api/orders/{oid}/review', t, dict(score=score, content='不错'))

# 丰收农资的新订单（待接单）和送货中订单
place(*buyers[0], [(m['pids'][0], 5), (m['pids'][1], 2)])
place(*buyers[1], [(m['pids'][3], 10)], pay='ONLINE')
oid = place(*buyers[3], [(m['pids'][2], 4)])
req('POST', f'/api/merchant/orders/{oid}/accept', m['token'], {})
oid = place(*buyers[4], [(m['pids'][5], 3), (m['pids'][4], 5)], pay='ONLINE')
req('POST', f'/api/merchant/orders/{oid}/accept', m['token'], {})
req('POST', f'/api/merchant/orders/{oid}/dispatch', m['token'], {})

# 求购
req('POST', '/api/wanted', buyers[1][0], dict(keyword='锄草剂 玉米田专用', note='要 20 瓶，苗后用', contactPhone='13600000001', lat=LAT + 0.01, lng=LNG))

# 赊账本
c = req('POST', '/api/merchant/credit/customers', m['token'], dict(name='李大爷', village='前进村', phone='13600000011'))
req('POST', f'/api/merchant/credit/customers/{c["id"]}/records', m['token'], dict(type='CREDIT', amount=660, note='复合肥 4 袋'))
req('POST', f'/api/merchant/credit/customers/{c["id"]}/records', m['token'], dict(type='REPAY', amount=300, note='还了一部分'))
c = req('POST', '/api/merchant/credit/customers', m['token'], dict(name='王婶', village='小李庄'))
req('POST', f'/api/merchant/credit/customers/{c["id"]}/records', m['token'], dict(type='CREDIT', amount=236, note='尿素 2 袋'))

# 运营后台：轮播图、一些搜不到的词
admin = req('POST', '/api/admin/login', body=dict(username='admin', password='admin123'))['token']
ban = Image.new('RGB', (750, 300), (36, 120, 70))
d = ImageDraw.Draw(ban)
d.text((50, 70), '春耕备肥季', font=font(72), fill=(255, 236, 170))
d.text((52, 180), '附近农资店 · 送货到村', font=font(36), fill=(255, 255, 255))
d.ellipse((560, 60, 720, 220), fill=(250, 200, 60))
buf = io.BytesIO(); ban.save(buf, 'JPEG')
boundary = uuid.uuid4().hex
body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="b.jpg"\r\nContent-Type: image/jpeg\r\n\r\n').encode() + buf.getvalue() + f'\r\n--{boundary}--\r\n'.encode()
r = urllib.request.Request(B + '/api/admin/files', data=body, method='POST')
r.add_header('Content-Type', 'multipart/form-data; boundary=' + boundary)
r.add_header('Authorization', 'Bearer ' + admin)
burl = json.loads(urllib.request.urlopen(r).read().decode())['data']['url']
req('POST', '/api/admin/banners', admin, dict(image=burl, title='春耕备肥', link='/pages/index/index'))
for kw in ['fuhefei', '尿素', '锄草剂', '锄草剂', '生根粉', '杀菌剂', 'ymzz']:
    urllib.request.urlopen(B + '/api/search?' + urllib.parse.urlencode(dict(keyword=kw, lat=LAT, lng=LNG)))
# 运营核实其中一款（演示牌复合肥），其余保持“待核实”
sku = req('GET', '/api/skus/lookup?barcode=6901234567892')
req('POST', f'/api/admin/standard-products/{sku["id"]}/verify', admin)
print('done')
