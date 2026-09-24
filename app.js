const form = document.querySelector('#wall-form');
const bagCount = document.querySelector('#bag-count');
const areaResult = document.querySelector('#area-result');
const widthResult = document.querySelector('#width-result');
const depthResult = document.querySelector('#depth-result');
const resultHint = document.querySelector('#result-hint');
const cameraModal = document.querySelector('#camera-modal');
const cameraVideo = document.querySelector('#camera-video');
const cameraStatus = document.querySelector('#camera-status');
const cameraStep = document.querySelector('#camera-step');
const cameraPointsLayer = document.querySelector('#camera-points');
let cameraStream;
let cameraPoints = [];
let cameraMode = 'reference';

const formatNumber = (value, decimals = 2) => value.toLocaleString('nl-NL', {
  minimumFractionDigits: decimals,
  maximumFractionDigits: decimals
});

form.addEventListener('submit', (event) => {
  event.preventDefault();

  const height = Number(document.querySelector('#height').value);
  const length = Number(document.querySelector('#length').value);
  const jointWidth = Number(document.querySelector('#joint-width').value);
  const jointDepth = Number(document.querySelector('#joint-depth').value);

  if ([height, length, jointWidth, jointDepth].some((value) => value <= 0 || Number.isNaN(value))) {
    resultHint.textContent = 'Gebruik positieve getallen voor alle maten.';
    return;
  }

  const area = height * length;
  const bags = Math.ceil(area / 2.5);

  bagCount.textContent = bags;
  areaResult.textContent = `${formatNumber(area)} m²`;
  widthResult.textContent = `${formatNumber(jointWidth, 1)} mm`;
  depthResult.textContent = `${formatNumber(jointDepth, 1)} mm`;
  resultHint.textContent = `${bags} ${bags === 1 ? 'zak' : 'zakken'} × 2,5 m² = voldoende voor ${formatNumber(area)} m² muur.`;
});

const setCameraStatus = (message) => { cameraStatus.textContent = message; };

const resetCameraMeasurement = () => {
  cameraPoints = [];
  cameraMode = 'reference';
  cameraPointsLayer.innerHTML = '';
  cameraStep.textContent = 'Tik referentie aan';
  setCameraStatus('Tik de twee uiteinden van de referentie aan.');
};

document.querySelector('#camera-button').addEventListener('click', async () => {
  cameraModal.classList.add('is-open');
  cameraModal.setAttribute('aria-hidden', 'false');
  resetCameraMeasurement();
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    setCameraStatus('Camera werkt alleen via HTTPS of localhost. Open de online app op je telefoon.');
    return;
  }
  try {
    cameraStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { exact: 'environment' } }, audio: false });
    cameraVideo.srcObject = cameraStream;
    setCameraStatus('Tik de twee uiteinden van de referentie aan.');
  } catch (error) {
    const message = error.name === 'NotAllowedError'
      ? 'Cameratoegang is geweigerd. Geef toestemming in je browserinstellingen.'
      : 'Camera kon niet starten. Controleer of een andere app de camera gebruikt.';
    setCameraStatus(message);
  }
});

document.querySelector('#close-camera').addEventListener('click', () => {
  cameraModal.classList.remove('is-open');
  cameraModal.setAttribute('aria-hidden', 'true');
  cameraStream?.getTracks().forEach((track) => track.stop());
});

document.querySelector('#reset-camera').addEventListener('click', resetCameraMeasurement);

cameraVideo.addEventListener('pointerup', (event) => {
  if (!cameraVideo.videoWidth || cameraMode === 'done') return;
  const bounds = cameraVideo.getBoundingClientRect();
  const point = { x: event.clientX - bounds.left, y: event.clientY - bounds.top };
  cameraPoints.push(point);
  const marker = document.createElement('span');
  marker.className = 'camera-point';
  marker.dataset.number = cameraPoints.length;
  marker.style.left = `${(point.x / bounds.width) * 100}%`;
  marker.style.top = `${(point.y / bounds.height) * 100}%`;
  cameraPointsLayer.append(marker);
  if (cameraPoints.length === 2 && cameraMode === 'reference') {
    cameraMode = 'height';
    cameraStep.textContent = 'Tik muurhoogte aan';
    setCameraStatus('Goed. Tik nu de twee uiteinden van de muurhoogte aan.');
  } else if (cameraPoints.length === 4 && cameraMode === 'height') {
    cameraMode = 'length';
    cameraStep.textContent = 'Tik muurlengte aan';
    setCameraStatus('Bijna klaar. Tik de twee uiteinden van de muurlengte aan.');
  } else if (cameraPoints.length === 6) {
    const distance = (first, second) => Math.hypot(second.x - first.x, second.y - first.y);
    const reference = Number(document.querySelector('#reference-length').value);
    const referencePixels = distance(cameraPoints[0], cameraPoints[1]);
    if (reference <= 0 || referencePixels < 5) {
      setCameraStatus('De referentie is te klein. Meet opnieuw met twee punten verder uit elkaar.');
      return;
    }
    const scale = reference / referencePixels;
    const height = distance(cameraPoints[2], cameraPoints[3]) * scale;
    const length = distance(cameraPoints[4], cameraPoints[5]) * scale;
    document.querySelector('#height').value = height.toFixed(2);
    document.querySelector('#length').value = length.toFixed(2);
    document.querySelector('#wall-form').requestSubmit();
    setCameraStatus(`Maten ingevuld: ${formatNumber(height)} m hoog × ${formatNumber(length)} m lang.`);
    cameraStep.textContent = 'Opnieuw meten';
    cameraMode = 'done';
  }
});

document.querySelector('#camera-measure').addEventListener('click', () => {
  if (cameraMode === 'done') resetCameraMeasurement();
  else setCameraStatus('Gebruik het camerabeeld en tik steeds de twee uiteinden aan.');
});
